import java.io.IOException;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import sun.misc.Signal;
import sun.misc.SignalHandler;

class CompletionResult {
    public List<String> possibilities = null;
    public boolean modifyEnter = false;

    public CompletionResult(List<String> possibilities, boolean modifyEnter) {
        this.possibilities = possibilities;
        this.modifyEnter = modifyEnter;
    }

    public CompletionResult(List<String> possibilities) {
        this.possibilities = possibilities;
    }

    public CompletionResult() {

    }
}

interface CompletionProvider {
    public CompletionResult getCompletionPossibilities(String line, String wordUnderCursor);
}

public class PromptTerminal {

    private static final String SPECIAL_CHARS = "!#@";

    public static final String SAVE_CURSOR = "\u001B[s"; // or "\0337"
    public static final String RESTORE_CURSOR = "\u001B[u"; // or "\0338"
    public static final String MOVE_TO_START_AND_CLEAR = "\r\u001B[K";
    public static final String MOVE_UP_ONE_LINE = "\u001B[1A"; // or "\033[A"
    public static final String MOVE_RIGHT = "\033[C"; // or "\033[A"
    public static final String MOVE_LEFT = "\033[D";
    public static final String RESET_COLOR = "\033[0m"; // Reset to default color
    public static final String INVERT_COLOR = "\033[7m"; // Invert foreground and background
    private static final int ESC = 27;
    private static final int LEFT_BRACKET = 91;
    private static final int SHIFT_TAB_CODE = 90;

    private CompletionProvider completionProvider;
    private InputStreamReader stdind_reader = new InputStreamReader(System.in);
    private final List<String> PROMPT_HISTORY = new ArrayList<String>();

    private boolean running = true;
    private String prompt = "";
    private int cursorPosition = 0;
    private int historyPosition = 0;
    private StringBuilder prompt_buffer = new StringBuilder();
    private boolean completionEnabled = false;

    public PromptTerminal(CompletionProvider completionProvider) {
        this.completionProvider = completionProvider;
        this.setResizeHandler();
    }

    public void setPrompt(String newPrompt) {
        this.prompt = newPrompt;
    }

    public boolean shouldQuit() {
        return !this.running;
    }

    public void enableCompletion() {
        this.completionEnabled = true;
    }

    public void disableCompletion() {
        this.completionEnabled = false;
    }

    public void clear() {
        // Clear the screen
        System.out.print("\033[H\033[2J");
        // Move the cursor to the bottom of the terminal
        System.out.print("\033[999B\r");
        System.out.flush();
    }

    private void setResizeHandler() {
        // Setting lê handler for SIGWINCH (window change signal)
        Signal.handle(new Signal("WINCH"), new SignalHandler() {
            @Override
            public void handle(Signal sig) {
                updateNlines();
            }
        });
    }

    private boolean isDelimiter(char c) {
        return c == '-' || c == '/' || c == '!' || c == '@' || c == '#' || c == '.' || Character.isWhitespace(c);
    }

    private boolean isDelimiter(char c, String ignore) {
        for (char i : ignore.toCharArray()) {
            if (c == i) {
                return false;
            }
        }

        boolean base = c == '-' || c == '/' || c == '!' || c == '@' || c == '#' || c == '.'
                || Character.isWhitespace(c);
        return base;
    }

    private void deleteWord() {
        String user = prompt_buffer.toString();

        boolean earlyStop = false;
        while (cursorIsInBounds(-1) && isDelimiter(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
            earlyStop = true;
            prompt_buffer.deleteCharAt(cursorPosition);
        }
        while (!earlyStop && cursorIsInBounds(-1) && !isDelimiter(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
            prompt_buffer.deleteCharAt(cursorPosition);
        }
        cursorSnapToInBounds();
    }

    private boolean cursorIsInBounds(int cursorPosition, int i) {
        if (cursorPosition + i > prompt_buffer.length() - 1) {
            return false;
        } else if (cursorPosition + i < 0) {
            return false;
        }
        return true;
    }

    private int getStartIndexOfWordUnderCursor() {
        String user = prompt_buffer.toString();

        boolean earlyStop = false;
        String ignore = "!/.-#@";
        int startPosition = cursorPosition;
        // while (cursorIsInBounds(startPosition, -1) &&
        // isDelimiter(user.charAt(startPosition - 1), ignore)) {
        // startPosition -= 1;
        // earlyStop = true;
        // }
        while (!earlyStop && cursorIsInBounds(startPosition, -1)
                && !isDelimiter(user.charAt(startPosition - 1), ignore)) {
            startPosition -= 1;
        }
        startPosition = Math.clamp(startPosition, 0, prompt_buffer.length());

        return startPosition;
    }

    private void moveFowardWord() throws IOException, InterruptedException {
        String user = prompt_buffer.toString();

        boolean earlyStop = false;
        while (cursorIsInBounds() && isDelimiter(user.charAt(cursorPosition))) {
            earlyStop = true;
            cursorPosition += 1;
        }
        while (!earlyStop && cursorIsInBounds() && !isDelimiter(user.charAt(cursorPosition))) {
            cursorPosition += 1;
        }
        cursorSnapToInBounds();
    }

    private void moveBackWord() throws IOException, InterruptedException {
        String user = prompt_buffer.toString();

        boolean earlyStop = false;
        while (cursorIsInBounds(-1) && isDelimiter(user.charAt(cursorPosition - 1))) {
            earlyStop = true;
            cursorPosition -= 1;
        }
        while (!earlyStop && cursorIsInBounds(-1) && !isDelimiter(user.charAt(cursorPosition - 1))) {
            cursorPosition -= 1;
        }
        cursorSnapToInBounds();
    }

    private void cursorSnapToInBounds() {
        if (cursorPosition > prompt_buffer.length() - 1) {
            cursorPosition = prompt_buffer.length();
        }

        else if (cursorPosition < 0) {
            cursorPosition = 0;
        }
    }

    private boolean cursorIsInBounds() {
        return cursorIsInBounds(0);
    }

    private boolean cursorIsInBounds(int i) {
        if (cursorPosition + i > prompt_buffer.length() - 1) {
            return false;
        } else if (cursorPosition + i < 0) {
            return false;
        }
        return true;
    }

    private void moveCursor(int amount) {
        cursorPosition += amount;
        if (cursorPosition > prompt_buffer.length() - 1) {
            cursorPosition = prompt_buffer.length();
        }

        else if (cursorPosition < 0) {
            cursorPosition = 0;
        }
    }

    private void deleteChar() {
        if (cursorPosition > 0) {
            prompt_buffer.deleteCharAt(--cursorPosition);
        }
    }

    // DONE: previous command with arrow up
    // DONE: autocomplete with TAB

    // TODO: Terminal is overriding previous messages when terminal height increases
    Integer nlines = 0;
    Integer maxNlines = 0;
    Integer terminalWidth = 0;
    Integer terminalHeight = 0;

    class CompletionStorage extends CompletionResult {
        String previousWord = null;
        int startWordIndex = 0;
        int index = 0;
        boolean lastUsed = false;

        @Override
        public String toString() {
            return "{" +
                    "previousWord='" + previousWord + "'" +
                    ", startWordIndex=" + startWordIndex +
                    ", index=" + index +
                    ", lastUsed=" + lastUsed +
                    ", completions"
                    + ((this.possibilities == null) ? "=" : ("[" + possibilities.size() + "]="))
                    + this.possibilities +
                    ", modifyEnter=" + modifyEnter +
                    "}";
        }

        public void reset() {
            this.lastUsed = false;
            this.index = 0;
            this.possibilities = null;
            this.previousWord = null;
            if (!this.lastUsed) {
                this.modifyEnter = false;
            }
        }

    }

    private static int properMod(int a, int b) {
        return ((a % b) + b) % b;
    }

    private CompletionStorage handleAutoCompletion(CompletionStorage _cs, int val) {
        CompletionStorage cs = _cs;
        cs.lastUsed = true;
        if (cs.previousWord == null || (cs.possibilities != null && cs.possibilities.size() <= 1)) {
            cs.startWordIndex = getStartIndexOfWordUnderCursor();
            cs.previousWord = prompt_buffer.substring(cs.startWordIndex, cursorPosition);
        }

        // We garanteed no null pointer accessing because we check for nullness before
        // accessing size method using short circuiting
        if (completionEnabled && (cs.possibilities == null || cs.possibilities.size() <= 1)) {
            CompletionResult cr = completionProvider.getCompletionPossibilities(
                    prompt_buffer.toString(), cs.previousWord);
            cs.modifyEnter = cr.modifyEnter;
            cs.possibilities = cr.possibilities;
        }

        if (cs.possibilities != null && cs.possibilities.size() > 0) {
            cs.index = properMod(cs.index + val, cs.possibilities.size());
            String completion = cs.possibilities.get(cs.index);

            prompt_buffer.delete(cs.startWordIndex, cursorPosition);
            prompt_buffer.insert(cs.startWordIndex, completion);

            cursorPosition = cs.startWordIndex + completion.length();
            prompt_buffer.setLength(cursorPosition);
        }
        return cs;

    }

    public String readLine() throws IOException, InterruptedException {
        cursorPosition = 0;
        StringBuilder sb = new StringBuilder();

        updateNlines();
        clearPrompt(sb);
        displayPrompt(sb, terminalWidth, terminalHeight);
        System.out.print(sb);
        System.out.flush();
        CompletionStorage cs = new CompletionStorage();
        while (running) {
            sb.setLength(0);

            char c = (char) stdind_reader.read();
            // Reset the state for completion
            // Both possibilities are close together on purpose

            if (c == '\t') {
                cs = handleAutoCompletion(cs, 1);
            } else {
                cs.lastUsed = false;
            }

            if (c == '\r' || c == '\n') {
                if (!cs.modifyEnter) {

                    // Move to next line && Move to beginning of next line
                    // Move cursor to the beginning of the line
                    System.out.print("\n\r" + "\033[G" + "\033[K");
                    System.out.flush();
                    String result = prompt_buffer.toString();
                    prompt_buffer.setLength(0);
                    nlines = 0;
                    maxNlines = 0;
                    PROMPT_HISTORY.add(result);
                    historyPosition = PROMPT_HISTORY.size();
                    return result;
                }
            } else if (c == 127 || c == 8) { // Backspace
                deleteChar();
            } else if (c == ESC) { // ESC
                char next = (char) stdind_reader.read();
                if (next == '[') {
                    next = (char) stdind_reader.read();

                    if (next == SHIFT_TAB_CODE) { // Shift+Tab
                        cs = handleAutoCompletion(cs, -1);
                    } else {
                        cs.lastUsed = false;
                    }

                    if (next == 'A') { // Up arrow
                        if (PROMPT_HISTORY.size() > 0) {
                            historyPosition -= 1;
                            historyPosition = Math.clamp(historyPosition, 0, PROMPT_HISTORY.size() - 1);
                            prompt_buffer.setLength(0);
                            prompt_buffer.append(PROMPT_HISTORY.get(historyPosition));
                            cursorPosition = prompt_buffer.length();

                        }

                    } else if (next == 'B') { // Down arrow
                        if (PROMPT_HISTORY.size() > 0) {
                            historyPosition += 1;
                            historyPosition = Math.clamp(historyPosition, 0, PROMPT_HISTORY.size() - 1);
                            prompt_buffer.setLength(0);
                            prompt_buffer.append(PROMPT_HISTORY.get(historyPosition));
                            cursorPosition = prompt_buffer.length();
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
            } else if ((c >= 32 && c < 127) || c == '´') { // Printable ASCII characters
                prompt_buffer.insert(cursorPosition++, c);
            } else if (c == 23) { // Ctrl+W
                deleteWord();
            } else if (c == 3) { // Ctrl+C
                System.out.print("\r\n");
                System.out.println("Ctrl + C detected exiting...");
                running = false;
                return "";
            }

            if (!cs.lastUsed || (cs.previousWord == null && cs.possibilities != null && cs.possibilities.size() == 1)) {
                cs.reset();
            }

            updateNlines();
            clearPrompt(sb);
            displayPrompt(sb, terminalWidth, terminalHeight);
            System.out.print(sb);
            System.out.flush();
        }

        return "";

    }

    public void moveCursorUp(StringBuilder sb, int rows) {
        if (rows > 0) {
            // ANSI escape code to move the cursor up by the specified number of rows
            sb.append("\033[" + rows + "A");
        }
    }

    public void setCursorColumn(StringBuilder sb, int column) {
        sb.append(String.format("\033[%dG", column));
    }

    public void setCursorRow(int row) {
        // System.out.print(String.format("\033[%dd", row));
        System.out.print("\033[" + row + ";1H"); // Move cursor to the last row, column 1

    }

    // - Position the Cursor:
    // \033[<L>;<C>H Or \033[<L>;<C>f
    public void setCursorPosition(int row, int column) {
        // \033 is the ESC character in octal
        System.out.print(String.format("\033[%d;%dH", row, column));
    }

    public void updateTerminalSize() {
        try {
            int[] size = getTerminalSize();
            terminalHeight = size[0];
            terminalWidth = size[1];
        } catch (IOException | InterruptedException e) {
            System.out.println("\rFailed to update terminal size: " + e.getMessage());
            toCookedMode();
            System.out.println("Goodbye :)");
            System.exit(69);
        }
    }

    public void updateNlines() {
        updateTerminalSize();
        if (prompt == null || prompt_buffer == null) {
            return;
        }
        int wholePromptLength = prompt.length() + prompt_buffer.length();
        int newNlines = (wholePromptLength - 1) / (terminalWidth);
        maxNlines = Math.max(newNlines, maxNlines);
        nlines = newNlines;
    }

    public void clearPrompt(StringBuilder operations) {
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

    public void updatePromptCursor(StringBuilder sb, int terminalWidth, int terminalHeight) {
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
        // int manyLefts = prompt_buffer.length() - cursorPosition;
        // for (int i = 0; i < manyLefts; i++) {
        // // sb.append("\033[D"); // Move cursor left
        // }
        // assert row > 0;
        moveCursorUp(sb, row);
        if (column == 1 && row >= 0) {
            setCursorColumn(sb, terminalWidth + 1);
            sb.append(prompt_buffer.toString().charAt(cursorPosition)); // Move cursor right
            // sb.append("\033[C"); // Move cursor right
        } else {
            setCursorColumn(sb, column);
        }
        // System.out.println(sb.toString() + row + " : " + column);
    }

    // DONE: See if the prompt needs to stay down, or if keeping it up is fine
    // https://tldp.org/HOWTO/Bash-Prompt-HOWTO/x361.html
    private void displayPrompt(StringBuilder sb, int terminalWidth, int terminalHeight) {
        // DONE: Clear old lines when we delete something
        updateNlines();

        int column = ((prompt.length() + cursorPosition) % (terminalWidth)) + 1;
        String user = prompt_buffer.toString();

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
    private String[] ttyConfig;

    private String stty(String args) throws IOException, InterruptedException {
        String[] cmd = { "/bin/sh", "-c", "stty " + args + " < /dev/tty" };

        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();

        byte[] output = p.getInputStream().readAllBytes();
        return new String(output).trim();
    }

    public void toRawMode() throws IOException, InterruptedException {
        ttyConfig = stty("-g").split(" ");
        stty("raw -echo");
    }

    public void toCookedMode() {
        try {
            if (ttyConfig != null) {
                stty(String.join(" ", ttyConfig));
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\nTerminal has been restored to 'cooked' mode.");
    }

    private int getTerminalWidth() throws IOException, InterruptedException {
        String size = stty("size");
        String[] dimensions = size.split(" ");
        return Integer.parseInt(dimensions[1]); // [1] is the number of columns
    }

    private int[] getTerminalSize() throws IOException, InterruptedException {
        String size = stty("size");
        String[] dimensions = size.split(" ");
        return new int[] { Integer.parseInt(dimensions[0]), Integer.parseInt(dimensions[1]) }; // [1] is the number of
    }

    // Function to move the cursor to the last row
    private void moveCursorToLastRow(int terminalHeight) {
        System.out.print("\033[" + terminalHeight + ";1H"); // Move cursor to the last row, column 1
        System.out.flush();
    }

    private int getTerminalHeight() throws IOException, InterruptedException {
        String size = stty("size");
        String[] dimensions = size.split(" ");
        return Integer.parseInt(dimensions[0]);
    }

    public void print(String text) {
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

}
