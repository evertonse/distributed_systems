import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Base64;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
// import java.util.concurrent.atomic.AtomicInteger;

import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.Exchange.DeclareOk;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class ChatClient {
    private static InputStreamReader stdind_reader = new InputStreamReader(System.in);
    private static final String RABBITMQ_HOST = "44.199.104.169"; // Elastic IP from aws "localhost";
    private static final String RABBITMQ_PORT = "15672"; // Default management plugin port
    private static final String RABBITMQ_USERNAME = "admin";
    private static final String RABBITMQ_PASSWORD = "password";
    private static final boolean DEBUG = true;
    private static final List<String> PROMPT_HISTORY = new ArrayList<String>();
    private static final String SPECIAL_CHARS = "!#@";

    private static boolean chat_client_running = false;

    private static final String SAVE_CURSOR = "\u001B[s"; // or "\0337"
    private static final String RESTORE_CURSOR = "\u001B[u"; // or "\0338"
    private static final String MOVE_TO_START_AND_CLEAR = "\r\u001B[K";
    private static final String MOVE_UP_ONE_LINE = "\u001B[1A"; // or "\033[A"
    private static final String MOVE_RIGHT = "\033[C"; // or "\033[A"
    private static final String MOVE_LEFT = "\033[D";

    private static final String PROMPT_DEFAULT = ">>";
    private static final String FILES_DEFAULT_FOLDER = "assets";

    private static Channel channel;
    private static Connection connection = null;
    private static String username;
    private static String recipient;
    private static String group;
    private static String prompt;
    private static int cursorPosition = 0;
    private static int historyPosition = 0;
    private static AMQP.BasicProperties amqp_props = null;
    private static StringBuilder editable_prompt_buffer = new StringBuilder();
    private static final String[] HOSTS = { RABBITMQ_HOST, "localhost" };
    private static String currentHost = HOSTS[0];
    private static final int CONNECTION_TIMEOUT = 5000;

    private static final String[] POSSIBLE_COMMANDS = {
            /* Etapa 2 */ "addGroup", "removeGroup", "addUser", "delFromGroup",
            /* Etapa 3 */ "upload",
            /* Etapa 5 */ "listUsers", "listGroups",
            /* Final ***/ "help"
    };

    public static void clearTerminal() {
        // Clear the screen
        System.out.print("\033[H\033[2J");
        // Move the cursor to the bottom of the terminal
        System.out.print("\033[999B\r");
        System.out.flush();
    }

    public static void setResizeHandler() {
        // Setting lê handler for SIGWINCH (window change signal)
        Signal.handle(new Signal("WINCH"), new SignalHandler() {
            @Override
            public void handle(Signal sig) {
                updateNlines();
            }
        });
    }

    private static boolean isValidUsername(String username) {
        if (username == "" || username.contains(" ")) {
            return false;
        }
        for (char c : SPECIAL_CHARS.toCharArray()) {
            if (username.indexOf(c) != -1) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidGroupName(String groupName) {
        if (groupName == "") {
            return false;
        }
        for (char c : SPECIAL_CHARS.toCharArray()) {
            if (groupName.indexOf(c) != -1) {
                return false;
            }
        }
        return true;
    }

    public static String handleListUsersCommand(String group) {
        List<String> names = apiGetQueuesBoundToExchange(group);
        if (names == null || names.size() == 0) {
            return "No users added to group \"" + group + "\".";
        }
        String commaSeparatedNames = String.join(", ", names);
        return commaSeparatedNames;
    }

    public static String handleListGroupsCommand() {
        List<String> names = apiGetExchangers();
        if (names == null || names.size() == 0) {
            return "No groups were created yet.";
        }

        String commaSeparatedNames = String.join(", ", names);
        return commaSeparatedNames;
    }

    public static String handleUploadCommand(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "File not found: " + filePath;
        }

        String destination = (group != null) ? group : recipient;
        if (destination == null) {
            return ("Entre no prompt de algum grupo ou pessoa para enviar um arquivo.");
        } else {
            // Start a new thread for file upload
            new Thread(() -> {
                try {
                    sendFileMessage(file);
                } catch (IOException e) {
                    printWithPrompt("Failed to upload the file: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }

        return ("Enviando \"" + filePath + "\" para " + destination + ".");
    }

    public static void sendFileMessage(File file) throws IOException {
        String mimeType = Files.probeContentType(file.toPath());
        byte[] msg = MessageUtils.createFileMessage(username, group, file, mimeType);

        if (group != null) {
            sendGroupMessage(FILE_TRANSFER_PREFIX + group, msg);
            printWithPrompt(String.format("Arquivo \" %s\" foi enviado para #%s.\n\r", file.toString(), group));
        } else if (recipient != null) {
            // Send to individual recipient
            String whereTo = FILE_TRANSFER_PREFIX + recipient;
            channel.basicPublish("", whereTo, null, msg);
            printWithPrompt(String.format("Arquivo \" %s\" foi enviado para @%s.\n\r", file.toString(), recipient));
        } else {
            printWithPrompt("No recipient or group selected. Please select a recipient or group first.\n\r");
        }
    }

    private static final String FILE_TRANSFER_PREFIX = "file_transfer@";

    public static void declareFileTransferQueue(String username) throws IOException {
        String queueName = FILE_TRANSFER_PREFIX + username;
        channel.queueDeclare(queueName, true, false, false, null);
    }

    private static void handleCommand(String command) throws IOException {
        String[] parts = command.substring(1).split(" ");
        StringBuilder sb = new StringBuilder();

        switch (parts[0]) {
            case "addGroup":
                if (parts.length > 1) {
                    String groupName = parts[1];
                    if (isValidGroupName(groupName)) {
                        String info = createGroup(groupName);
                        sb.append(info);
                    } else {
                        sb.append("Group name can't be empty, neither have special characters" + SPECIAL_CHARS);
                    }

                } else {
                    sb.append("Usage: !addGroup <group_name>");
                }
                break;
            case "removeGroup":
                if (parts.length > 1) {
                    String info = removeGroup(parts[1]);
                    sb.append(info);
                } else {
                    sb.append("Usage: !removeGroup <group_name>");
                }
                break;
            case "addUser":
                if (parts.length > 2) {
                    for (int i = 1; i < parts.length - 1; i++) {
                        String msg = addUserToGroup(parts[parts.length - 1], parts[i]);
                        sb.append(msg);
                        if (i != parts.length - 2) {
                            sb.append("\n\r");
                        }
                    }
                } else {
                    sb.append("Usage: !addUser <user_name_first> .. <user_name_last> <group_name>");
                }
                break;
            case "delFromGroup":
                if (parts.length > 2) {
                    for (int i = 1; i < parts.length - 1; i++) {
                        String msg = removeUserFromGroup(parts[parts.length - 1], parts[i]);
                        sb.append(msg);
                        if (i != parts.length - 2) {
                            sb.append("\n\r");
                        }
                    }
                } else {
                    sb.append("Usage: !delFromGroup <user_name_first> .. <user_name_last> <group_name>");
                }
                break;
            case "upload":
                if (parts.length > 1) {
                    for (int i = 1; i < parts.length; i++) {
                        sb.append(handleUploadCommand(parts[i]));
                        if (i != parts.length - 1) {
                            sb.append("\n\r");
                        }
                    }
                } else {
                    sb.append("Usage: !upload <filepath>");
                }
                break;

            case "listUsers":
                if (parts.length == 1 && group != null) {
                    sb.append(handleListUsersCommand(group));
                } else if (parts.length == 2) {
                    sb.append(handleListUsersCommand(parts[1]));
                } else {
                    sb.append("Usage: !listUsers <group_name> or !listUsers when inside a group prompt");
                }
                break;

            case "listGroups":
                if (parts.length == 1) {
                    sb.append(handleListGroupsCommand());
                } else {
                    sb.append("Usage: !listGroups");
                }
                break;

            case "help":
                sb.append(helpMenuString());
                break;
            default:
                sb.append("Unknown command: " + (parts[0] == "" ? "<empty>" : parts[0]) + "\n\r");
                sb.append(helpMenuString());
                break;
        }
        // note: doesn't matter if it's empty
        System.out.println(sb.toString());
        System.out.flush();
    }

    private static List<String> completeCommand(String currentText) {
        List<String> possibleCompletion = new ArrayList<String>();
        for (String command : POSSIBLE_COMMANDS) {
            if (('!' + command).startsWith(currentText)) {
                possibleCompletion.add('!' + command);
            }
        }
        return possibleCompletion;
    }

    private static String helpMenuString() {
        StringBuilder sb = new StringBuilder();
        // NOTE: Need \n\r because the cursor might be changed on these lines
        // Effectively printing on the middle of column on that "new" line
        String pad = "    ";
        sb.append("Available commands:\n\r")
                .append(pad + "!addGroup <group_name>: Creates a new group with the specified name.\n\r")
                .append(pad
                        + "!addUser <user_name_first> .. <user_name_last> <group_name>: Adds the specified users to the given group.\n\r")
                .append(pad + "@<user_name>: Set conversation to a certain user.\n\r")
                .append(pad + "#<group>: Set conversation to a certain group.\n\r")
                .append(pad + "!delFromGroup <user_names...> <group_name>\n\r")
                .append(pad + "!removeGroup <group_name>\n\r")
                .append(pad + "!upload <filepaths...>: send files to either group or user\n\r")
                .append(pad + "!listUsers <group>: list users on a certain group\n\r")
                .append(pad + "!listGroups: list all groups\n\r")
                .append(pad + "!help: Displays this help menu.");
        return sb.toString();
    }

    public static void sendTextMessage(String text) throws IOException {
        byte[] msg = MessageUtils.createTextMessage(username, group, text, "text/plain");

        if (group != null) {
            // Assumes that we're setting group to null everytime we change to recepient
            // mode
            sendGroupMessage(group, msg);
        } else if (recipient != null) {
            // Handle direct messages
            channel.basicPublish("", recipient, null, msg);
        }
    }

    public static void createUser(String username) throws IOException {
        //
        // NOTE: If we change any of the properties of a queue
        // that is already created that triggers a exception.
        // TODO: Maybe we need to handle that, should we care?
        //

        channel.queueDeclare(username, false, false, false, null);
        declareFileTransferQueue(username);
    }

    public static String createGroup(String groupName) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (groupExists(groupName)) {
            sb.append("Group '" + groupName + "' already exists.");
        } else {
            DeclareOk _ok = channel.exchangeDeclare(groupName, "fanout", true);
            // Create the file tranfer exchanger as well and bind both
            _ok = channel.exchangeDeclare(FILE_TRANSFER_PREFIX + groupName, "fanout", true);
            sb.append("Group '" + groupName + "' created.");
        }
        String info = addUserToGroup(groupName, username);
        sb.append("\n\r" + info);
        return sb.toString();
    }

    private static boolean groupExists(String groupName) {
        // Using a Temporary channel
        // see: https://groups.google.com/g/rabbitmq-users/c/ZTfVwe_HYXc
        try (Channel tempChannel = connection.createChannel()) {
            tempChannel.exchangeDeclarePassive(groupName);
            return true;
        } catch (AlreadyClosedException e) {
            printWithPrompt("ERROR: Channel already closed " + e.getMessage());
            return false;
        } catch (Exception e) {
            // The exception indicates that the exchange doesn't exist or another issue
            // occurred
            return false;
        }
    }

    // https://stackoverflow.com/questions/3457305/how-can-i-check-whether-a-rabbitmq-message-queue-exists-or-not
    // queue.declare is an idempotent operation. So, if you run it once, twice, N
    // times, the result will still be the same.
    // If you want to ensure that the queue exists, just declare it before using it.
    // Make sure you declare it with the same durability, exclusivity,
    // auto-deleted-ness every time, otherwise you'll get an exception.
    // Another ideia:
    // @Autowired
    // public RabbitAdmin rabbitAdmin;
    //
    //
    // //###############get you queue details##############
    // Properties properties = rabbitAdmin.getQueueProperties(queueName);
    //
    // //do your custom logic
    // if( properties == null)
    // {
    // createQueue(queueName);
    // }
    private static boolean queueExists(String queueName) {
        try (Channel tempChannel = connection.createChannel()) {
            tempChannel.queueDeclarePassive(queueName);
            return true;
        } catch (AlreadyClosedException e) {
            System.err.println("Channel already closed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // Exception indicates that the queue doesn't exist
            // Java style XDDD
            return false;
        }
    }

    private static String addUserToGroup(String groupName, String userName) {
        try {
            String queueName = userName;

            // Bind the user's queue to the group exchange
            if (groupExists(groupName)) {
                // We check if queue exist, and assume that the file transfer queue also exists
                // NOTE: is this right?
                if (queueExists(queueName)) {
                    channel.queueBind(queueName, groupName, "");
                    // Also bind to the file queue to the file exchager
                    channel.queueBind(FILE_TRANSFER_PREFIX + queueName, FILE_TRANSFER_PREFIX + groupName, "");
                    return ("User '" + userName + "' added to group '" + groupName + "'.");
                } else {
                    return ("User '" + userName + "' does not exist.");
                }
            } else {
                return ("Tried to add '" + userName + "' to nonexistent group called '" + groupName + "'.");

            }
        } catch (IOException e) {
            return ("Error adding user '" + userName + "' to group '" + groupName + "'" + e.getMessage()
                    + e.getCause());
        }
    }

    public static void sendGroupMessage(String groupName, byte[] message) throws IOException {
        // Publish a message to the fanout exchange
        channel.basicPublish(groupName, "", null, message);
    }

    public static Connection tryConnection(ConnectionFactory factory) throws IOException, TimeoutException {
        Thread loadingThread = new Thread(() -> {
            String[] animationFrames = { "|", "/", "-", "\\" };
            int i = 0;

            while (!Thread.currentThread().isInterrupted()) {
                System.out
                        .print(MOVE_TO_START_AND_CLEAR + "\r" + animationFrames[i++ % animationFrames.length]
                                + " Connecting  to " + currentHost);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        int hostIndex = 0;
        if (DEBUG) {
            currentHost = "localhost";
        }

        loadingThread.start();
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
                System.out.println("\r\n\rFailed to connect to " + currentHost + ".");
            }

            // Switch to the next host
            hostIndex = (hostIndex + 1) % HOSTS.length;
        }

        loadingThread.interrupt();
        System.out.println("\n\rConnected to " + currentHost + "!");
        return connection;
    }

    private static String removeGroup(String groupName) {
        try {
            if (groupExists(groupName)) {
                channel.exchangeDelete(groupName);
                return ("Group '" + groupName + "' removed.");
            }
            return ("Group '" + groupName + "' does not exist.");

        } catch (IOException e) {
            return String.format("Error when removing group '%s': %\n\rCause: %s", groupName, e.getMessage(),
                    e.getCause());
        }
    }

    private static boolean isUserBoundToGroup(String groupName, String username) {
        return true;
    }

    private static String removeUserFromGroup(String groupName, String user) {
        try {
            if (groupExists(groupName)) {
                if (queueExists(user)) {
                    if (isUserBoundToGroup(groupName, user)) {
                    }
                    channel.queueUnbind(user, groupName, "");
                    return String.format("User '%s' removed from group '%s'", user, groupName);
                } else {
                    return String.format("User '%s' doesn't exist '%s'", user, groupName);
                }
            }
            return String.format("Tried to removed user '%s' from nonexistent group '%s'", user, groupName);
        } catch (IOException e) {
            return String.format("Erro ao remover %s do grupo %s: %s", user, groupName, e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException, TimeoutException {

        clearTerminal();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(RABBITMQ_USERNAME);
        factory.setPassword(RABBITMQ_PASSWORD);
        factory.setVirtualHost("/");

        Thread receiveMessageThread = new Thread(ChatClient::receiveMessages);
        Thread receiveFileThread = new Thread(ChatClient::receiveFiles);

        try {
            Connection connection = tryConnection(factory);
            channel = connection.createChannel();
            chat_client_running = true;
            setResizeHandler();

            System.out
                    .println(
                            "Raw mode enabled.\n\rPress 'Crtl+c' to quit.\n\rType '!help' to see available commands after entering your username.");
            setTerminalToRawMode();

            prompt = "User: ";
            username = readLine();
            while (chat_client_running && !isValidUsername(username)) {
                System.out.println(
                        "Username can't contain spaces, special characters (" + SPECIAL_CHARS
                                + "),  neither be empty.");
                username = readLine();
            }
            prompt = PROMPT_DEFAULT;
            // Durable queue, TODO: check what durable even does? IMPORTANT: If we set
            // durable to true, we get IOException
            createUser(username);

            receiveMessageThread.start();
            receiveFileThread.start();

            while (chat_client_running) {
                if (group != null) {
                    prompt = "#" + group + ">> ";
                } else if (recipient != null) {
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
                    group = null;
                } else if (input.startsWith("#")) {
                    group = input.substring(1);
                    if (!groupExists(group)) {
                        System.out.println("Group '" + group + "' doesn't exist.");
                        System.out.flush();
                        group = null;
                    }
                } else if (input.startsWith("!")) {
                    handleCommand(input);
                } else {
                    sendTextMessage(input);
                }
            }
            receiveMessageThread.interrupt();
            receiveFileThread.interrupt();
        } catch (IOException | InterruptedException | TimeoutException e) {
            System.out.println("\rFailed to connect: " + e);
        } finally {
            restoreTerminal();
            channel.close();
            // NOTE(excyber): important to make sure threads doesn't hang
            // closing the connection already closes the channel I think, because
            // it does not hangs even if we ONLY close the connection
            connection.close();
            System.out.println("Goodbye :)");
            if (!DEBUG) {
                // NOTE: This makes not print the exceptions in other threads
                System.exit(0); // Forcefully quit all threads
            }
            receiveMessageThread.interrupt();
            receiveFileThread.interrupt();
        }
    }

    private static boolean isDelimiter(char c) {
        return c == '-' || c == '/' || c == '!' || c == '@' || c == '#' || c == '.' || Character.isWhitespace(c);

    }

    private static void deleteWord() {
        String user = editable_prompt_buffer.toString();

        while (cursorIsInBounds(-1) && Character.isWhitespace(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
            editable_prompt_buffer.deleteCharAt(cursorPosition);
        }
        while (cursorIsInBounds(-1) && !isDelimiter(user.charAt(cursorPosition - 1))) {
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
        while (cursorIsInBounds() && !isDelimiter(user.charAt(cursorPosition))) {
            cursorPosition += 1;
        }
        cursorSnapToInBounds();
    }

    private static void moveBackWord() throws IOException, InterruptedException {
        String user = editable_prompt_buffer.toString();

        while (cursorIsInBounds(-1) && Character.isWhitespace(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
        }
        while (cursorIsInBounds(-1) && !isDelimiter(user.charAt(cursorPosition - 1))) {
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

    // DONE: previous command with arrow up
    // TODO: autocomplete with TAB
    // TODO: Terminal is overriding previous messages when terminal height increases

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
        List<String> completionPossibilities = null;
        int completionIndex = 0;
        String completionPreviousWord = null;
        while (chat_client_running) {
            sb.setLength(0);
            char c = (char) stdind_reader.read();
            // Reset the state for completion
            // Both possibilities are close together on purpose
            if (c != '\t') {
                completionIndex = 0;
                completionPossibilities = null;
                completionPreviousWord = null;
            }

            if (c == '\t') {
                // Handle Tab key for auto-completion
                if (completionPreviousWord == null) {
                    completionPreviousWord = editable_prompt_buffer.substring(0, cursorPosition);
                }
                if (completionPossibilities == null) {
                    completionPossibilities = completeCommand(completionPreviousWord);
                }
                if (completionPossibilities != null && completionPossibilities.size() > 0) {
                    completionIndex = (completionIndex + 1) % completionPossibilities.size();
                    String completion = completionPossibilities.get(completionIndex);
                    editable_prompt_buffer.setLength(0);
                    editable_prompt_buffer.append(completion);
                    cursorPosition = editable_prompt_buffer.length();
                }
            } else if (c == '\r' || c == '\n') {
                // Move to next line && Move to beginning of next line
                // Move cursor to the beginning of the line
                System.out.print("\n\r" + "\033[G" + "\033[K");
                System.out.flush();
                String result = editable_prompt_buffer.toString();
                editable_prompt_buffer.setLength(0);
                nlines = 0;
                maxNlines = 0;
                PROMPT_HISTORY.add(result);
                historyPosition = PROMPT_HISTORY.size();
                return result;
            } else if (c == 127 || c == 8) { // Backspace
                deleteChar();
            } else if (c == 27) { // ESC
                char next = (char) stdind_reader.read();
                if (next == '[') {
                    next = (char) stdind_reader.read();
                    if (next == 'A') { // Up arrow
                        if (PROMPT_HISTORY.size() > 0) {
                            historyPosition -= 1;
                            historyPosition = Math.clamp(historyPosition, 0, PROMPT_HISTORY.size() - 1);
                            editable_prompt_buffer.setLength(0);
                            editable_prompt_buffer.append(PROMPT_HISTORY.get(historyPosition));
                            cursorPosition = editable_prompt_buffer.length();

                        }

                    } else if (next == 'B') { // Down arrow
                        if (PROMPT_HISTORY.size() > 0) {
                            historyPosition += 1;
                            historyPosition = Math.clamp(historyPosition, 0, PROMPT_HISTORY.size() - 1);
                            editable_prompt_buffer.setLength(0);
                            editable_prompt_buffer.append(PROMPT_HISTORY.get(historyPosition));
                            cursorPosition = editable_prompt_buffer.length();
                        }
                    } else if (next == 'C') { // Right arrow
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

    // DONE: See if the prompt needs to stay down, or if keeping it up is fine
    // https://tldp.org/HOWTO/Bash-Prompt-HOWTO/x361.html
    public static void displayPrompt(StringBuilder sb, int terminalWidth, int terminalHeight) {
        // DONE: Clear old lines when we delete something
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

        System.out.println("\nTerminal has been restored to 'cooked' mode.");
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

    // TODO: Maybe put a delay for readmessages, for when you're receiving too many
    // messages. sleep(2.0) something like that
    private static void receiveMessages() {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
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
                // NOTE: string + null in java is defined
                String group = msg.getGroup();
                sender = msg.getSender() + (group != null && group != "" ? ("#" + group) : "");
                hour = msg.getHour();
                date = msg.getDate();
            } catch (Exception e) {
                System.err.println("Failed to deserialize message: " + e.getMessage());
                return;
            }

            String timestamp = String.format("%s às %s", date, hour);

            printWithPrompt(String.format("(%s) %s diz: %s\n", timestamp, sender, msgContent));
        };

        try {
            channel.basicConsume(username, true, deliverCallback, consumerTag -> {
            });
        } catch (IOException e) {
            System.out.println("`receiveMessages` forcefully clased probably, here's the stack trace: ");
            e.printStackTrace();
        }
    }

    private static void printWithPrompt(String text) {
        updateNlines();
        StringBuilder sb = new StringBuilder();
        sb.append("\033[G" + "\033[K");
        sb.append(text);
        for (int i = 0; i < maxNlines; i++) {
            sb.append("\n"); // Move cursor up one line
        }

        for (int i = 0; i < maxNlines; i++) {
            sb.append(MOVE_UP_ONE_LINE); // Move cursor up one line
        }

        displayPrompt(sb, terminalWidth, terminalHeight);
        System.out.print(sb.toString());
        System.out.flush();
    }

    public static void saveReceivedFile(String filePath, byte[] fileContentBytes) throws IOException {
        Path path = Paths.get(filePath);
        printWithPrompt(filePath);
        Path parentDir = path.getParent();

        // Ensure the parent directories exist
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        Files.write(path, fileContentBytes);
    }

    private static void receiveFiles() {
        String queueName = FILE_TRANSFER_PREFIX + username;
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            byte[] messageDataBytes = delivery.getBody();

            String sender = "[unknown sender]";
            String date = "[unknown date]";
            String hour = "[unknown hour]";
            String fileName = null;
            byte[] fileContentBytes = null;
            // Deserialize the Message object
            MessageProtoBuffer.Message msg;
            try {
                msg = MessageUtils.fromBytes(messageDataBytes);
                MessageProtoBuffer.Content content = msg.getContent();
                fileContentBytes = content.getBody().toByteArray();
                fileName = content.getName();
                String group = msg.getGroup();
                sender = msg.getSender() + (group != null && group != "" ? ("#" + group) : "");
                hour = msg.getHour();
                date = msg.getDate();
            } catch (Exception e) {
                printWithPrompt("Failed to deserialize message: " + e.getMessage());
                return;
            }
            String filePath = FILES_DEFAULT_FOLDER + '/' + String.format(
                    "%s-%s-%s-%s", date, hour, sender, fileName)
                    .replace("/", "-")
                    .replace("\\", "-");

            saveReceivedFile(filePath, fileContentBytes);
            String timestamp = String.format("%s às %s", date, hour);
            printWithPrompt(String.format("(%s) Arquivo \"%s\" recebido de %s.\n", timestamp, fileName, sender));
        };

        try {

            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {
            });
        } catch (IOException e) {
            System.out.println("`receiveMessages` forcefully clased probably, here's the stack trace: ");
            e.printStackTrace();
        }
    }

    public static List<String> apiGetExchangers() {
        String username = "guest"; // RabbitMQ username
        String password = "guest"; // RabbitMQ password
        String vhost = "%2F"; // Virtual host, use "%2F" for the default vhost

        try {

            URL url = new URI("http://" + currentHost + ":" + RABBITMQ_PORT + "/api/exchanges/" + vhost).toURL();

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Set up basic authentication
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            conn.setRequestProperty("Content-Type", "application/json");

            int responseCode = conn.getResponseCode();
            // System.out.println("Response Code: " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Simple parsing to extract exchange names
            List<String> exchangeNames = extractExchangeNames(response.toString());

            String commaSeparatedNames = String.join(", ", exchangeNames);
            return exchangeNames;

        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            return null;
        }

    }

    private static List<String> extractExchangeNames(String jsonResponse) {
        List<String> names = new ArrayList<>();
        int index = 0;
        while ((index = jsonResponse.indexOf("\"name\":", index)) != -1) {
            int startQuote = jsonResponse.indexOf("\"", index + 7);
            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
            if (startQuote != -1 && endQuote != -1) {
                String name = jsonResponse.substring(startQuote + 1, endQuote);
                // Filter out default exchanges and the default "" (nameless) exchange
                // And also filter the transfer prefix exchangers
                if (!name.startsWith("amq.") && !name.isEmpty() && !name.startsWith(FILE_TRANSFER_PREFIX)) {
                    names.add(name);
                }
            }
            index = endQuote;
        }
        return names;
    }

    public static List<String> apiGetQueuesBoundToExchange(String exchangeName) {
        String username = "guest";
        String password = "guest";
        String vhost = "%2F"; // Virtual host, use "%2F" for the default vhost

        try {
            URL url = new URI(
                    "http://" + currentHost + ":" + RABBITMQ_PORT + "/api/exchanges/" + vhost + "/" + exchangeName
                            + "/bindings/source")
                    .toURL();

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // The most basic auth xD
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            conn.setRequestProperty("Content-Type", "application/json");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Simple parsing to extract queue names
            List<String> queueNames = extractQueueNames(response.toString());

            return queueNames;

        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static List<String> extractQueueNames(String jsonResponse) {
        List<String> names = new ArrayList<>();
        int index = 0;
        String searchFor = "\"destination\"";
        int searchForSize = searchFor.length();
        while ((index = jsonResponse.indexOf(searchFor, index)) != -1) {
            index = jsonResponse.indexOf(":", index + searchForSize);

            int startIndex = jsonResponse.indexOf("\"", index);
            int endIndex = jsonResponse.indexOf("\"", startIndex + 1);
            if (endIndex != -1 && startIndex != -1) {
                String queueName = jsonResponse.substring(startIndex + 1, endIndex);
                names.add(queueName);
            }
            index = endIndex;
        }
        return names;
    }

}
