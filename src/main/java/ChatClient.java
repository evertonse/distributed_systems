import java.io.IOException;

import java.io.InputStreamReader;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeoutException;
// import java.util.concurrent.atomic.AtomicInteger;

import com.rabbitmq.client.*;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class ChatClient {
    private static InputStreamReader stdind_reader = new InputStreamReader(System.in);
    private static final String RABBITMQ_HOST = "44.199.104.169"; // Elastic IP from aws "localhost";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";
    private static final boolean DEBUG = true;

    private static boolean chat_client_running = false;

    private static final String SAVE_CURSOR = "\u001B[s"; // or "\0337"
    private static final String RESTORE_CURSOR = "\u001B[u"; // or "\0338"
    private static final String MOVE_TO_START_AND_CLEAR = "\r\u001B[K";
    private static final String MOVE_UP_ONE_LINE = "\u001B[1A"; // or "\033[A"
    private static final String MOVE_RIGHT = "\033[C"; // or "\033[A"
    private static final String MOVE_LEFT = "\033[D";

    private static final String PROMPT_DEFAULT = ">>";

    private static Channel channel;
    private static String username;
    private static String recipient;
    private static String group;
    private static String prompt;
    private static int cursorPosition = 0;
    private static AMQP.BasicProperties amqp_props = null;
    private static StringBuilder editable_prompt_buffer = new StringBuilder();
    private static String currentHost = RABBITMQ_HOST;
    private static final String[] HOSTS = { RABBITMQ_HOST, "localhost" };
    private static final int CONNECTION_TIMEOUT = 5000;

    public static void clearTerminal() {
        // Clear the screen
        System.out.print("\033[H\033[2J");
        System.out.flush();

        // Move the cursor to the bottom of the terminal
        System.out.print("\033[999B");
        System.out.flush();
    }

    public static void setResizeHandler() {
        // Set up the handler for SIGWINCH (window change signal)
        Signal.handle(new Signal("WINCH"), new SignalHandler() {
            @Override
            public void handle(Signal sig) {
                updateNlines();
                // maxNlines = nlines;
                // prompt = String.format("nlines::%d, maxNlines::%d", nlines, maxNlines);
                // StringBuilder sb = new StringBuilder();
                // clearPrompt(sb);
                // displayPrompt(sb, terminalWidth, terminalHeight);
                // System.out.print(sb);
                // System.out.flush();

            }
        });
    }

    private static void handleCommand(String command) throws IOException {
        String[] parts = command.split(" ");
        switch (parts[0]) {
            // TODO: error handling, client should let the user know it's missing stuff
            // or too much arguments
            case "!addGroup":
                if (parts.length > 1) {
                    createGroup(parts[1]);
                }
                break;
            case "!addUser":
                if (parts.length > 1) {
                    addUserToGroup(parts[1]);
                }
                break;
            // Add more commands as needed
        }
    }

    public static void sendMessage(String text) throws IOException {
        byte[] msg = MessageUtils.createMessage(username, null, text, "text/plain");

        if (group != null) {
            sendGroupMessage(group, msg);
        } else {
            // Handle direct messages
            channel.basicPublish("", recipient, null, msg);
        }
    }

    public static void addUserToGroup(String groupName) throws IOException {
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, groupName, "");
        System.out.println("Joined group '" + groupName + "'");
    }

    public static void createGroup(String groupName) throws IOException {
        // Declare a fanout exchange for the group
        channel.exchangeDeclare(groupName, "fanout");
        System.out.println("Group '" + groupName + "' created.");
    }

    public static void addUserToGroup(String groupName, String queueName) throws IOException {
        // Bind the user's queue to the fanout exchange
        channel.queueBind(queueName, groupName, "");
        System.out.println("User added to group '" + groupName + "'.");
    }

    public static void sendGroupMessage(String groupName, byte[] message) throws IOException {
        // Publish a message to the fanout exchange
        channel.basicPublish(groupName, "", null, message);
        System.out.println("Message sent to group '" + groupName + "'.");
    }

    public static Connection tryConnection(ConnectionFactory factory) throws IOException, TimeoutException {
        Thread loadingThread = new Thread(() -> {
            String[] animationFrames = { "|", "/", "-", "\\" };
            int i = 0;

            while (!Thread.currentThread().isInterrupted()) {
                System.out
                        .print("\r" + animationFrames[i++ % animationFrames.length] + " Connecting  to " + currentHost);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        loadingThread.start();

        int hostIndex = 0;
        Connection connection = null;

        factory.setConnectionTimeout(CONNECTION_TIMEOUT);
        factory.setHandshakeTimeout(CONNECTION_TIMEOUT);

        while (connection == null) {
            currentHost = HOSTS[hostIndex];

            if (DEBUG && hostIndex == 0) {
                currentHost = "localhost";
            }

            factory.setHost(currentHost);
            try {
                Thread.sleep(400); // initial wait just to show animation
                try {
                    connection = factory.newConnection();
                } catch (Exception e) {
                    System.out.println("\nSwitching to the next host...");
                }
            } catch (Exception e) {
                System.out.println("\nFailed to connect to " + currentHost + ".");
            }

            // Switch to the next host
            hostIndex = (hostIndex + 1) % HOSTS.length;
        }

        loadingThread.interrupt();
        System.out.println("\nConnected to " + currentHost + "!");
        return connection;
    }

    public static void main(String[] args) throws IOException, TimeoutException {

        clearTerminal();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(USERNAME);
        factory.setPassword(PASSWORD);
        factory.setVirtualHost("/");

        try {
            Connection connection = tryConnection(factory);
            channel = connection.createChannel();
            chat_client_running = true;
            setResizeHandler();

            System.out.println("Raw mode enabled. Press 'Crtl+c' to quit.");
            setTerminalToRawMode();

            prompt = "User: ";
            username = readLine();
            while (chat_client_running && username == "" || username.contains(" ")) {
                System.out.println("username can't contain spaces, neither be empty.");
                username = readLine();
            }
            prompt = PROMPT_DEFAULT;
            channel.queueDeclare(username, false, false, false, null);

            Thread receiveThread = new Thread(ChatClient::receiveMessages);
            receiveThread.start();

            while (chat_client_running) {
                if (recipient != null) {
                    prompt = "@" + recipient + ">> ";
                } else {
                    prompt = PROMPT_DEFAULT;
                }

                //
                // Periodically check for terminal size changes,
                // I don't know if this will be fast enough on AWS machines.
                // Even the rendering might be too slow.
                // I'll just keep developing for now, then we'll see when we port to
                // AWS
                //
                // updateTerminalSizeThread.start();
                String input = readLine();

                if (input.startsWith("@")) {
                    recipient = input.substring(1);
                } else if (input.startsWith("!")) {
                    handleCommand(input);
                } else {
                    if (recipient != null) {
                        sendMessage(input);
                    }
                }
            }
            receiveThread.interrupt();
        } catch (IOException | InterruptedException | TimeoutException e) {
            System.out.println("\rFailed to connect: " + e.getMessage());
        } finally {
            restoreTerminal();
            System.out.println("Goodbye :)");
            System.exit(0); // Forcefully quit all threads
        }
    }

    private static void deleteWord() {
        String user = editable_prompt_buffer.toString();

        while (cursorIsInBounds(-1) && Character.isWhitespace(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
            editable_prompt_buffer.deleteCharAt(cursorPosition);
        }
        while (cursorIsInBounds(-1) && !Character.isWhitespace(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
            editable_prompt_buffer.deleteCharAt(cursorPosition);
        }
        cursorSnapToInBounds();
    }

    private static void moveFowardWord() throws IOException, InterruptedException {
        String user = editable_prompt_buffer.toString();

        while (cursorIsInBounds() && Character.isWhitespace(user.charAt(cursorPosition))) {
            cursorPosition += 1;
        }
        while (cursorIsInBounds() && !Character.isWhitespace(user.charAt(cursorPosition))) {
            cursorPosition += 1;
        }
        cursorSnapToInBounds();
    }

    private static void moveBackWord() throws IOException, InterruptedException {
        String user = editable_prompt_buffer.toString();

        while (cursorIsInBounds(-1) && Character.isWhitespace(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
        }
        while (cursorIsInBounds(-1) && !Character.isWhitespace(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
        }
        cursorSnapToInBounds();
    }

    private static void cursorSnapToInBounds() {
        if (cursorPosition > editable_prompt_buffer.length() - 1) {
            cursorPosition = editable_prompt_buffer.length();
        }

        else if (cursorPosition < 0) {
            cursorPosition = 0;
        }
    }

    private static boolean cursorIsInBounds() {
        return cursorIsInBounds(0);
    }

    private static boolean cursorIsInBounds(int i) {
        if (cursorPosition + i > editable_prompt_buffer.length() - 1) {
            return false;
        } else if (cursorPosition + i < 0) {
            return false;
        }
        return true;
    }

    private static void moveCursor(int amount) {
        cursorPosition += amount;
        if (cursorPosition > editable_prompt_buffer.length() - 1) {
            cursorPosition = editable_prompt_buffer.length();
        }

        else if (cursorPosition < 0) {
            cursorPosition = 0;
        }
    }

    private static void deleteChar() {
        if (cursorPosition > 0) {
            editable_prompt_buffer.deleteCharAt(--cursorPosition);
        }
    }

    // TODO: previous command with arrow up
    // TODO: autocomplete with TAB

    static Integer nlines = 0;
    static Integer maxNlines = 0;
    static Integer terminalWidth = 0;
    static Integer terminalHeight = 0;

    private static String readLine() throws IOException, InterruptedException {
        cursorPosition = 0;
        StringBuilder sb = new StringBuilder();

        updateNlines();
        clearPrompt(sb);
        displayPrompt(sb, terminalWidth, terminalHeight);
        System.out.print(sb);
        System.out.flush();

        while (chat_client_running) {
            sb.setLength(0);
            char c = (char) stdind_reader.read();
            if (c == '\r' || c == '\n') {
                // Move to next line && Move to beginning of next line
                // Move cursor to the beginning of the line
                System.out.print("\n\r" + "\033[G" + "\033[K");
                System.out.flush();
                String result = editable_prompt_buffer.toString();
                editable_prompt_buffer.setLength(0);
                nlines = 0;
                maxNlines = 0;
                return result;
            } else if (c == 127 || c == 8) { // Backspace
                deleteChar();
            } else if (c == 27) { // ESC
                char next = (char) stdind_reader.read();
                if (next == '[') {
                    next = (char) stdind_reader.read();
                    if (next == 'C') { // Right arrow
                        moveCursor(1);
                    } else if (next == 'D') { // Left arrow
                        moveCursor(-1);
                    } else if (next == '1') { // not a direction
                        next = (char) stdind_reader.read();
                        if (next == ';') { // \u001B[1;5D
                            next = (char) stdind_reader.read();
                            if (next == '5') {
                                next = (char) stdind_reader.read();
                                if (next == 'C') { // Right arrow
                                    moveFowardWord();
                                } else if (next == 'D') { // Left arrow
                                    moveBackWord();
                                }
                            }
                        }
                    }

                }
            } else if (c >= 32 && c < 127) { // Printable ASCII characters
                editable_prompt_buffer.insert(cursorPosition++, c);
                // if (cursorPosition == editable_prompt_buffer.length()) {
                // System.out.print(c);
                // } else {
                //
                // System.out.print(SAVE_CURSOR);
                // System.out.print("\033[K"); // Clear the line from the cursor to the end
                // // System.out.print(editable_prompt_buffer.substring(cursorPosition - 1));
                // System.out.print(RESTORE_CURSOR); // Clear the line from the cursor to the
                // System.out.print(MOVE_RIGHT); // Move cursor right
                // }
            } else if (c == 23) { // Ctrl+W
                deleteWord();
            } else if (c == 3) { // Ctrl+C
                System.out.print("\r\n");
                System.out.println("Ctrl + C detected exiting...");
                chat_client_running = false;
                return "";
            }
            updateNlines();
            clearPrompt(sb);
            displayPrompt(sb, terminalWidth, terminalHeight);
            System.out.print(sb);
            System.out.flush();
        }
        return "";
    }

    public static void moveCursorUp(StringBuilder sb, int rows) {
        if (rows > 0) {
            // ANSI escape code to move the cursor up by the specified number of rows
            sb.append("\033[" + rows + "A");
        }
    }

    public static void setCursorColumn(StringBuilder sb, int column) {
        sb.append(String.format("\033[%dG", column));
    }

    public static void setCursorRow(int row) {
        // System.out.print(String.format("\033[%dd", row));
        System.out.print("\033[" + row + ";1H"); // Move cursor to the last row, column 1

    }

    // - Position the Cursor:
    // \033[<L>;<C>H Or \033[<L>;<C>f
    public static void setCursorPosition(int row, int column) {
        // \033 is the ESC character in octal
        System.out.print(String.format("\033[%d;%dH", row, column));
    }

    public static void updateTerminalSize() {
        try {
            int[] size = getTerminalSize();
            terminalHeight = size[0];
            terminalWidth = size[1];
        } catch (IOException | InterruptedException e) {
            System.out.println("\rFailed to update terminal size: " + e.getMessage());
            restoreTerminal();
            System.out.println("Goodbye :)");
            System.exit(69);
        }
    }

    public static void updateNlines() {
        updateTerminalSize();
        if (prompt == null || editable_prompt_buffer == null) {
            return;
        }
        int wholePromptLength = prompt.length() + editable_prompt_buffer.length();
        int newNlines = (wholePromptLength - 1) / (terminalWidth);
        maxNlines = Math.max(newNlines, maxNlines);
        nlines = newNlines;
    }

    public static void clearPrompt(StringBuilder operations) {
        updateNlines();
        operations.append("\033[" + 9999 + "B");
        // "\033[99999;%0H"
        operations.append("\033[G"); // Move cursor to the beginning of the line
        operations.append("\033[K"); // Clear the line from the cursor to the end
        // for (int i = 0; i < maxNlines; i++) {
        for (int i = 0; i < maxNlines; i++) {
            operations.append("\033[A"); // Move cursor up one line
            operations.append("\033[G"); // Move cursor to the beginning of the line
            operations.append("\033[K"); // Clear the line from the cursor to the end
        }
        boolean keep_promt_height_at_minimum_possible = false;
        if (keep_promt_height_at_minimum_possible) {
            operations.append("\033[" + 9999 + "B");
            for (int i = 0; i < nlines; i++) {
                operations.append("\033[A"); // Move cursor up one line
            }
        }
    }

    public static void updatePromptCursor(StringBuilder sb, int terminalWidth, int terminalHeight) {
        if (true) {
            return;
        }

        updateNlines();
        int row = nlines - ((prompt.length() + cursorPosition - 1) / terminalWidth);
        // XXX: Column is wrong
        int column = ((prompt.length() + cursorPosition) % (terminalWidth)) + 1;

        // System.out.println(" column = " + column
        // + " terminalWidth = " + terminalWidth);
        // + " row = " + row
        // int manyLefts = editable_prompt_buffer.length() - cursorPosition;
        // for (int i = 0; i < manyLefts; i++) {
        // // sb.append("\033[D"); // Move cursor left
        // }
        // assert row > 0;
        moveCursorUp(sb, row);
        if (column == 1 && row >= 0) {
            setCursorColumn(sb, terminalWidth + 1);
            sb.append(editable_prompt_buffer.toString().charAt(cursorPosition)); // Move cursor right
            // sb.append("\033[C"); // Move cursor right
        } else {
            setCursorColumn(sb, column);
        }
        // System.out.println(sb.toString() + row + " : " + column);
    }

    // TODO: See if the prompt needs to stay down, or if keeping it up is fine
    // https://tldp.org/HOWTO/Bash-Prompt-HOWTO/x361.html
    public static void displayPrompt(StringBuilder sb, int terminalWidth, int terminalHeight) {
        // TODO: Clear old lines when we delete something
        updateNlines();

        int column = ((prompt.length() + cursorPosition) % (terminalWidth)) + 1;
        String user = editable_prompt_buffer.toString();
        // if (user.length() > ("" + column).length()) {
        // user = user.substring(0, user.length() - ("" + column).length()) + column;
        //
        // }

        sb.append("\033[G" + "\033[K");
        sb.append(prompt + user.substring(0, cursorPosition));
        sb.append(SAVE_CURSOR);

        sb.append(user.substring(cursorPosition, user.length()));
        sb.append(RESTORE_CURSOR);
        if (column == 1) {
            sb.append(user.charAt(cursorPosition - 1));

        }
    }

    // System.out.print("\033[2J"); // Clear the screen
    private static String[] ttyConfig;

    private static String stty(String args) throws IOException, InterruptedException {
        String[] cmd = { "/bin/sh", "-c", "stty " + args + " < /dev/tty" };

        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();

        byte[] output = p.getInputStream().readAllBytes();
        return new String(output).trim();
    }

    private static void setTerminalToRawMode() throws IOException, InterruptedException {
        // Runtime.getRuntime().addShutdownHook(new
        // Thread(ChatClient::restoreTerminal));
        ttyConfig = stty("-g").split(" ");
        stty("raw -echo");
    }

    private static void restoreTerminal() {
        try {
            if (ttyConfig != null) {
                stty(String.join(" ", ttyConfig));
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\nTerminal has been restored to `cooked` mode.");
    }

    private static int getTerminalWidth() throws IOException, InterruptedException {
        String size = stty("size");
        String[] dimensions = size.split(" ");
        return Integer.parseInt(dimensions[1]); // [1] is the number of columns
    }

    private static int[] getTerminalSize() throws IOException, InterruptedException {
        String size = stty("size");
        String[] dimensions = size.split(" ");
        return new int[] { Integer.parseInt(dimensions[0]), Integer.parseInt(dimensions[1]) }; // [1] is the number of
    }

    // Function to move the cursor to the last row
    private static void moveCursorToLastRow(int terminalHeight) {
        System.out.print("\033[" + terminalHeight + ";1H"); // Move cursor to the last row, column 1
        System.out.flush();
    }

    private static int getTerminalHeight() throws IOException, InterruptedException {
        String size = stty("size");
        String[] dimensions = size.split(" ");
        return Integer.parseInt(dimensions[0]);
    }

    // TODO: Slow readmessages, for when you're receiving too many messages
    private static void receiveMessages() {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            updateNlines();
            byte[] messageDataBytes = delivery.getBody();
            Map<String, Object> headers = delivery.getProperties().getHeaders();

            String correlationId = delivery.getProperties().getCorrelationId();
            String contentType = delivery.getProperties().getContentType();
            String routingKey = delivery.getEnvelope().getRoutingKey();

            String sender = "[unknown sender]";
            String msgContent = "[unknown message]";

            String date = "[unknown date]";
            String hour = "[unknown hour]";
            // Deserialize the Message object
            MessageProtoBuffer.Message msg;
            try {
                msg = MessageUtils.fromBytes(messageDataBytes);
                msgContent = msg.getContent().getBody().toStringUtf8();
                sender = msg.getSender();
                hour = msg.getHour();
                date = msg.getDate();
            } catch (Exception e) {
                System.err.println("Failed to deserialize message: " + e.getMessage());
                return;
            }

            String timestamp = String.format("%s às %s", date, hour);

            StringBuilder sb = new StringBuilder();

            boolean debug = false;
            clearPrompt(sb);
            sb.append("\033[G" + "\033[K");
            if (!debug) {
                sb.append(String.format("(%s) %s diz: %s\n", timestamp, sender, msgContent));
            } else {
                sb.append(String.format("correlationId(%s), contentType(%s) routingKey(%s), sender(%s)\n",
                        correlationId, contentType, routingKey, sender));
            }

            for (int i = 0; i < maxNlines; i++) {
                sb.append("\n"); // Move cursor up one line
            }

            for (int i = 0; i < maxNlines; i++) {
                sb.append(MOVE_UP_ONE_LINE); // Move cursor up one line
            }

            displayPrompt(sb, terminalWidth, terminalHeight);
            System.out.print(sb);
            System.out.flush();
        };

        try {
            channel.basicConsume(username, true, deliverCallback, consumerTag -> {
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
