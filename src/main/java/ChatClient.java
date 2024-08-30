import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

interface Command {
  public String getName();

  public String getHelp();

  public String execute(String[] parts) throws IOException;
}

public class ChatClient {

  private static final String RABBITMQ_HOST =
      "44.199.104.169"; // Elastic IP from aws "localhost";
  private static final String RABBITMQ_PORT =
      "15672"; // Default management plugin port
  private static final String RABBITMQ_USERNAME = "admin";
  private static final String RABBITMQ_PASSWORD = "password";
  private static final String SPECIAL_CHARS = "!#@-/";
  private static final boolean DEBUG = true;

  private static List<String> usersChat = null;
  private static List<String> groupsChat = null;

  private static final String PROMPT_DEFAULT = ">>";

  private static String username;
  private static String recipient;
  private static String group;

  private static final String[] RABBITMQ_HOSTS = {RABBITMQ_HOST, "localhost"};
  private static final String FILE_TRANSFER_PREFIX = "file_transfer@";
  private static RabbitMQProxy rabbit = null;

  // Some MB
  private static final long FILE_MAX_CHUNK_SIZE = 20L * 1024L * 1024L;
  // private static final long FILE_MAX_CHUNK_SIZE = 50L * 1024L * 1024L;
  // private static final long FILE_MAX_CHUNK_SIZE = 50L * 1024L;

  private static final ExecutorService executor =
      Executors.newCachedThreadPool();

  public static void main(String[] args) throws IOException, TimeoutException {

    terminal.clear();

    rabbit =
        new RabbitMQProxy(RABBITMQ_USERNAME, RABBITMQ_PASSWORD, RABBITMQ_HOSTS);

    Thread receiveTextThread =
        new Thread(() -> { rabbit.receiveText(username, terminal); });

    Thread receiveFileThread =
        new Thread(() -> { rabbit.receiveFiles(username, terminal); });

    try {

      rabbit.tryConnection(terminal);
      System.out.println("Raw mode enabled.\n\r"
                         + "Press 'Crtl+c' once to clear and again to quit.\n\r"
                         + "Type '!help' to see available commands after "
                         + "entering your username.\n\r"
                         + "Use 'TAB' to autocomplete.");

      terminal.toRawMode();

      terminal.setPrompt("User: ");
      username = terminal.readLine();
      while (!terminal.shouldQuit() && !isValidUsername(username)) {
        System.out.println(
            "Username can't contain spaces, special characters (" +
            SPECIAL_CHARS + "),  neither be empty.");
        username = terminal.readLine();
      }

      terminal.enableCompletion();
      terminal.setPrompt(PROMPT_DEFAULT);
      if (terminal.shouldQuit()) {
        return;
      }
      // Durable queue, TODO: check what durable even does? IMPORTANT: If we set
      // durable to true, we get IOException
      rabbit.createUser(username);

      receiveTextThread.start();
      receiveFileThread.start();

      while (!terminal.shouldQuit()) {
        if (group != null) {
          terminal.setPrompt("#" + group + ">> ");
        } else if (recipient != null) {
          terminal.setPrompt("@" + recipient + ">> ");
        } else {
          terminal.setPrompt(PROMPT_DEFAULT);
        }

        usersChat = null;
        groupsChat = null;
        String input = terminal.readLine();

        if (input.startsWith("@")) {
          String newRecipient = input.substring(1);
          if (!isValidUsername(newRecipient)) {
            System.out.println(
                "Usernames can't contain spaces, special characters (" +
                SPECIAL_CHARS + "),  neither be empty.");
          } else {
            recipient = newRecipient;
            group = null;
          }
        } else if (input.startsWith("#")) {
          group = input.substring(1);
          if (!rabbit.groupExists(group)) {
            terminal.print("Group '" + group + "' doesn't exist.\n\r");
            group = null;
          }
        } else if (input.startsWith("!")) {
          handleCommand(input);
        } else {
          if (!input.isEmpty() && input != null) {

            executor.execute(() -> {
              boolean ok =
                  rabbit.sendTextMessage(username, recipient, group, input);
              if (!ok) {
                terminal.print(
                    "'sendTextMessage': Could not send text message. \n\r");
              }
            });
          }
        }
      }
      receiveTextThread.interrupt();
      receiveFileThread.interrupt();
    } catch (IOException | InterruptedException e) {
      System.out.println("\rFailed with expection: " + e);
      if (DEBUG) {
        e.printStackTrace();
      }
    } finally {
      terminal.toCookedMode();
      executor.shutdown();
      // NOTE(excyber): important to make sure threads doesn't hang
      // closing the connection already closes the channel I think, because
      // it does not hangs even if we ONLY close the connection
      rabbit.shutdown();
      System.out.println("Goodbye :)");
      if (!DEBUG) {
        // NOTE: This makes not print the exceptions in other threads
        System.exit(0); // Forcefully quit all threads
      }
      receiveTextThread.interrupt();
      receiveFileThread.interrupt();
    }
  }

  private static final PromptTerminal terminal =
      new PromptTerminal(new CompletionProvider() {
        public CompletionResult getCompletionPossibilities(
            String line, String wordUnderCursor) {
          CompletionResult cr = new CompletionResult();
          if (line.startsWith("@")) {
            cr.possibilities = completeUsersChat(wordUnderCursor, "@");
          } else if (line.startsWith("#")) {
            cr.possibilities = completeGroupsChat(wordUnderCursor, "#");
          } else if (line.contains("!upload")) {
            cr.possibilities = completeFilePath(wordUnderCursor);
            if (wordUnderCursor.endsWith(File.separator)) {
              cr.modifyEnter = true;
            } else {
              cr.modifyEnter = false;
            }
          } else if (line.contains("!delFromGroup") ||
                     line.contains("!addUser")) {
            cr.possibilities = new ArrayList<String>();
            for (String string : completeGroupsChat(wordUnderCursor, "")) {
              cr.possibilities.add(string);
            }
            for (String string : completeUsersChat(wordUnderCursor, "")) {
              cr.possibilities.add(string);
            }
          } else if (line.contains("!removeGroup") ||
                     line.contains("!listUsers")) {
            cr.possibilities = new ArrayList<String>();
            for (String string : completeGroupsChat(wordUnderCursor, "")) {
              cr.possibilities.add(string);
            }
          } else if (!line.contains(" ")) {
            cr.possibilities = completeCommand(wordUnderCursor);
          } else {
          }
          return cr;
        }
      });

  private static boolean isValidUsername(String username) {
    if (username == null || username.isEmpty() || username.contains(" ")) {
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
    if (groupName.isEmpty()) {
      return false;
    }
    for (char c : SPECIAL_CHARS.toCharArray()) {
      if (groupName.indexOf(c) != -1) {
        return false;
      }
    }
    return true;
  }

  // No Args we get all users
  public static String handleListUsersCommand() {
    List<String> names = rabbit.apiGetAllQueues();
    if (names == null || names.size() == 0) {
      // Should never happen
      return "No users exists yet.";
    }
    String commaSeparatedNames = String.join(", ", names);
    return commaSeparatedNames;
  }

  public static String handleListUsersCommand(String group) {
    if (!rabbit.groupExists(group)) {
      return "Group \"" + group + "\"does not exist.";
    }

    List<String> names = rabbit.apiGetQueuesBoundToExchange(group);
    if (names == null || names.size() == 0) {
      return "No users added to group \"" + group + "\".";
    }
    String commaSeparatedNames = String.join(", ", names);

    return commaSeparatedNames;
  }

  public static String handleListGroupsCommand() {
    List<String> names = rabbit.apiGetExchangers();
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
    if (file.isDirectory()) {
      return String.format("File \"%s\" is a directory.", filePath);
    }

    String destination = (group != null) ? group : recipient;
    if (destination == null) {
      return (
          "Entre no prompt de algum grupo ou pessoa para enviar um arquivo.");
    }

    // Start a new thread for file upload
    int maxRetries = 3;
    int retryDelay = 5000; // 5 seconds
    new Thread(() -> {
      boolean success = false;
      for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
          rabbit.sendChunkedRetryingFileMessage(file, username, recipient,
                                                group, terminal);
          success = true;
          break; // Exit the loop if successful
        } catch (IOException e) {
          if (attempt <= maxRetries) {
            terminal.print(String.format(
                "\rTentativa %d/%d: Erro ao enviar o arquivo \"%s\". "
                    + "Tentando novamente em %d segundos...\n\r",
                attempt, maxRetries, file.getName(), retryDelay / 1000));

            // We sleep tight for a bit:)
            try {
              Thread.sleep(retryDelay);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              terminal.print(
                  "\rInterrompido durante a espera para nova tentativa.\n\r");
              break; // Exit the loop if interrupted
            }

          } else {
            terminal.print(String.format(
                "Falha ao enviar o arquivo \"%s\" após %d tentativas.\n\r",
                file.getName(), maxRetries));
          }
          if (DEBUG) {
            e.printStackTrace();
          }
        }
      }
      if (!success) {
        terminal.print(
            String.format("Não foi possível enviar o arquivo \"%s\". "
                              + "Por favor, tente novamente mais tarde.\n\r",
                          file.getName()));
      }
    }).start();

    return ("Enviando \"" + filePath + "\" para " + destination + ".");
  }

  private static void handleCommand(String command) throws IOException {
    String[] parts = command.substring(1).split(" ");
    StringBuilder sb = new StringBuilder();

    boolean matched = false;
    for (Command cmd : COMMANDS) {
      if (cmd.getName().equals(parts[0])) {
        sb.append(cmd.execute(parts));
        matched = true;
        break;
      }
    }
    if (!matched) {
      sb.append("Unknown command: " +
                (parts[0].isEmpty() ? "<empty>" : parts[0]) + "\n\r");
      sb.append("Use !help too see all commands available.");
    }
    // note: doesn't matter if it's empty
    System.out.println(sb.toString());
    System.out.flush();
  }

  private static List<String> completeCommand(String currentText) {
    List<String> possibleCompletion = new ArrayList<String>();
    for (Command cmd : COMMANDS) {
      String cmdName = cmd.getName();
      if (('!' + cmdName).startsWith(currentText)) {
        possibleCompletion.add('!' + cmdName);
      }
    }
    return possibleCompletion;
  }

  private static List<String> completeUsersChat(String currentText,
                                                String prefix) {
    // TODO: Cache this
    if (usersChat != null) {
      return usersChat;
    }
    List<String> possibleCompletion = new ArrayList<String>();
    for (String users : rabbit.apiGetAllQueues()) {
      if ((prefix + users).startsWith(currentText)) {
        possibleCompletion.add(prefix + users);
      }
    }
    usersChat = possibleCompletion;
    return possibleCompletion;
  }

  private static List<String> completeGroupsChat(String currentText,
                                                 String prefix) {
    // TODO: Cache this
    if (groupsChat != null) {
      return groupsChat;
    }
    List<String> possibleCompletion = new ArrayList<String>();
    for (String groups : rabbit.apiGetExchangers()) {
      if ((prefix + groups).startsWith(currentText)) {
        possibleCompletion.add(prefix + groups);
      }
    }
    groupsChat = possibleCompletion;
    return possibleCompletion;
  }

  public static List<String> completeFilePath(String currentText) {
    List<String> possibleCompletion = new ArrayList<>();

    File baseDir;
    String prefix;

    if (currentText.contains("/")) {
      int lastSlashIndex = currentText.lastIndexOf('/');
      String basePath = currentText.substring(0, lastSlashIndex + 1);
      prefix = currentText.substring(lastSlashIndex + 1);
      baseDir = new File(basePath.isEmpty() ? "." : basePath);
    } else {
      baseDir = new File(".");
      prefix = currentText;
    }

    // Now the real work begins
    File[] files = baseDir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (currentText.endsWith("/") || file.getName().startsWith(prefix)) {
          possibleCompletion.add(currentText +
                                 file.getName().substring(prefix.length()) +
                                 (file.isDirectory() ? "/" : ""));
        }
      }
    }

    return possibleCompletion;
  }

  private static String helpMenuString() {
    StringBuilder sb = new StringBuilder();
    // NOTE: Need \n\r because the cursor might be changed on these lines
    // Effectively printing on the middle of column on that "new" line

    String pad = "    ";

    sb.append("Available commands:\n\r");
    sb.append(pad + "@<user_name>: Set conversation to a certain user."
              + "\n\r");
    sb.append(pad + "#<group>: Set conversation to a certain group."
              + "\n\r");
    int len = COMMANDS.length;
    for (int i = 0; i < len; i++) {
      Command cmd = COMMANDS[i];
      sb.append(pad + cmd.getHelp());
      sb.append("\n\r");
    }
    sb.append("\n\rYou can use:\n\r")
        .append(pad + ("'Arrow' left or right move cursor (can be modified "
                       + "with 'Crtl').\n\r"))
        .append(pad + "'Ctrl+W' delete a word.\n\r")
        .append(pad + ("'TAB' to autocomplete (commands, filepaths, users, "
                       + "groups, etc ...)."));
    return sb.toString();
  }

  /////////////////////|///////////////|//////////////////////
  /////////////////////| Chat Commands |/////////////////////
  /////////////////////|///////////////|////////////////////

  private static final Command[] COMMANDS = {
      new Command(){public String getName(){return "addGroup";
}

public String getHelp() {
  return "!addGroup <group_name>: Creates a new group with the specified name.";
}

public String execute(String[] parts) throws IOException {
  if (parts.length > 1) {
    String groupName = parts[1];
    if (isValidGroupName(groupName)) {
      String info = rabbit.createGroup(username, groupName);
      return (info);
    } else {
      return ("Group name can't be empty, neither have special characters" +
              SPECIAL_CHARS);
    }

  } else {
    return ("Usage: !addGroup <group_name>");
  }
}
}
, new Command() {
  public String getName() { return "removeGroup"; }

  public String getHelp() { return "!removeGroup <group_name>"; }

  public String execute(String[] parts) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (parts.length > 1) {
      String info = rabbit.removeGroup(parts[1]);
      sb.append(info);
    } else {
      sb.append("Usage: !removeGroup <group_name>");
    }
    return sb.toString();
  }
}, new Command() {
  public String getName() { return "whoami"; }

  public String getHelp() { return "!whoami: Prints your own name"; }

  public String execute(String[] parts) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(username);
    return sb.toString();
  }
}, new Command() {
  public String getName() { return "addUser"; }

  public String getHelp() {
    return "!addUser <user_name_first> .. <user_name_last> <group_name>: "
        + "Adds the specified"
        + " users to the given group.";
  }

  public String execute(String[] parts) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (parts.length > 2) {
      for (int i = 1; i < parts.length - 1; i++) {
        String msg = rabbit.addUserToGroup(parts[parts.length - 1], parts[i]);
        sb.append(msg);
        if (i != parts.length - 2) {
          sb.append("\n\r");
        }
      }
    } else {
      sb.append(
          "Usage: !addUser <user_name_first> .. <user_name_last> <group_name>");
    }
    return sb.toString();
  }
}, new Command() {
  public String getName() { return "delFromGroup"; }

  public String getHelp() {
    return "!delFromGroup <user_names...> <group_name>";
  }

  public String execute(String[] parts) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (parts.length > 2) {
      for (int i = 1; i < parts.length - 1; i++) {
        String msg =
            rabbit.removeUserFromGroup(parts[parts.length - 1], parts[i]);
        sb.append(msg);
        if (i != parts.length - 2) {
          sb.append("\n\r");
        }
      }
    } else {
      sb.append("Usage: !delFromGroup <user_name_first> .. <user_name_last> "
                + "<group_name>");
    }
    return sb.toString();
  }
}, new Command() {
  public String getName() { return "upload"; }

  public String getHelp() {
    return "!upload <filepaths...>: send files to either group or user";
  }

  public String execute(String[] parts) throws IOException {
    StringBuilder sb = new StringBuilder();
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
    return sb.toString();
  }
}, new Command() {
  public String getName() { return "listUsers"; }

  public String getHelp() {
    return "!listUsers <group>: list users on a certain group";
  }

  public String execute(String[] parts) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (parts.length == 1 && group != null) {
      sb.append(handleListUsersCommand(group));
    } else if (parts.length == 2) {
      sb.append(handleListUsersCommand(parts[1]));
    } else {
      sb.append("Usage: !listUsers <group_name> or !listUsers when inside a "
                + "group prompt");
    }
    return sb.toString();
  }
}, new Command() {
  public String getName() { return "listAllUsers"; }

  public String getHelp() { return "!listAllUsers: list all users"; }

  public String execute(String[] parts) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (parts.length == 1) {
      sb.append(handleListUsersCommand());
    } else {
      sb.append("Usage: !listAllUsers, no extra args");
    }
    return sb.toString();
  }
}, new Command() {
  public String getName() { return "listGroups"; }

  public String getHelp() { return "!listGroups: list all groups"; }

  public String execute(String[] parts) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (parts.length == 1) {
      sb.append(handleListGroupsCommand());
    } else {
      sb.append("Usage: !listGroups");
    }
    return sb.toString();
  }
}, new Command() {
  public String getName() { return "help"; }

  public String getHelp() { return "!help: Displays this help me"; }

  public String execute(String[] parts) throws IOException {
    return helpMenuString();
  }
}
}
;
}
