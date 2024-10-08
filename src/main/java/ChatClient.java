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

  private static final String RABBITMQ_HOST_ELASTIC_IP = "44.199.104.169"; // Elastic IP from aws;
  private static final String RABBITMQ_LOAD_BALANCER_1 = "LB-tcp-271e400b721179f8.elb.us-east-1.amazonaws.com";
  
  private static final String RABBITMQ_PORT =
      "15672"; // Default management plugin port
  private static final String RABBITMQ_USERNAME = "admin";
  private static final String RABBITMQ_PASSWORD = "password";
  private static final String SPECIAL_CHARS = "!#@-/";

  private static final String USER_NAME_HELP
    = "Username can't contain spaces, special characters (" + SPECIAL_CHARS + "),  neither be empty.";

  private static final boolean DEBUG = System.getProperty("DEBUG", "false").equals("true");
  private static final boolean CHECK_IF_USER_IS_IN_GROUP_BEFORE = false;

  private static List<String> usersChat = null;
  private static List<String> groupsChat = null;

  private static final String PROMPT_DEFAULT = ">>";

  private static String username;
  private static String recipient;
  private static String group;

  //
  // Order Matters, it'll try from left to right
  //
  private static String[] RABBITMQ_HOSTS =
    { RABBITMQ_LOAD_BALANCER_1, RABBITMQ_HOST_ELASTIC_IP, "localhost"};
    // {RABBITMQ_LOAD_BALANCER_1};
    // {RABBITMQ_HOST_ELASTIC_IP};
    // {};
    // {"18.207.238.82"};

  private static RabbitMQProxy rabbit = null;

  private static final ExecutorService executor =
      Executors.newCachedThreadPool();

  static boolean GUI = false;

  public static void main(String[] args) throws IOException, TimeoutException {

    final String OS = System.getProperty("os.name").toLowerCase();
    if (OS.contains("win")) {
        System.out.println("Windows OS detected, because windows terminal does not support terminal editing and ansi escape code, we are using GUI\n");
        GUI = true;
    }

    if (args.length > 0) {
      for (String arg : args) {
        if (GUI == false && arg.equals("gui")) {
          GUI = true;
        }

        if (arg.contains(".")) {
          ChatClient.RABBITMQ_HOSTS = new String[]{ arg, RABBITMQ_LOAD_BALANCER_1, RABBITMQ_HOST_ELASTIC_IP, "localhost"};
        }
      }
    }

    if (DEBUG) {
      System.out.println("Running in DEBUG mode");
    }


    if (GUI) {
        terminal = new GUIPromptTerminal(completion);
        GUI = true;
        System.out.println("Running using GUI\n");
    } else {
        System.out.println("Assuming Unix env\n");
        terminal = new PromptTerminal(completion);
    }

    terminal.clear();
    rabbit =
        new RabbitMQProxy(RABBITMQ_USERNAME, RABBITMQ_PASSWORD, RABBITMQ_HOSTS, RABBITMQ_HOST_ELASTIC_IP);

    Thread receiveTextThread =
        new Thread(() -> { rabbit.receiveText(username, terminal); });

    Thread receiveFileThread =
        new Thread(() -> { rabbit.receiveFiles(username, terminal); });

    try {

      rabbit.tryConnection(terminal);
      String startHelp = (
        "Type '!help' to see available commands after "
        + "entering your username.\n\r"
        + "Use 'TAB' to autocomplete.\n\r"
        + "Type your username (User: you)."
      );

      if (!GUI) {
        startHelp =
        "Raw mode enabled.\n\r"
        + "Press 'Crtl+c' once to clear and again to quit.\n\r"
        + startHelp;
      }

      if(GUI) {
        terminal.clear();
        terminal.print(startHelp);
      } else {
        System.out.println(startHelp);
      }


      terminal.toRawMode();

      terminal.setPrompt("User: ");
      username = terminal.readLine();
      while (!terminal.shouldQuit() && !isValidUsername(username)) {
        if(GUI) {
          terminal.print(USER_NAME_HELP);
        } else {
          System.out.println(USER_NAME_HELP);
        }
        username = terminal.readLine();
      }

      terminal.enableCompletion();
      terminal.setPrompt(PROMPT_DEFAULT);
      if (terminal.shouldQuit()) {
        return;
      }
      // DONE: Durable
      // IMPORTANT: If we change durable seetting on existing queue, we get
      // IOException
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
            if(GUI) {
              terminal.print(USER_NAME_HELP);
            } else {
              System.out.println(USER_NAME_HELP);
            }
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
              if (group != null && CHECK_IF_USER_IS_IN_GROUP_BEFORE  && !rabbit.isUserInGroup(group, username)) {
                terminal.print("Você não está no group: \"" + group +
                               "\".\n\r");
              }
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
  static CompletionProvider completion = new CompletionProvider() {
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
  };

  // private static Printable terminal;
  private static PromptTerminal terminal;

  /////////////////////|////////////|//////////////////////
  /////////////////////| Utilities  |/////////////////////
  /////////////////////|////////////|////////////////////
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

  /////////////////////|///////////////////|//////////////////////
  /////////////////////| Commands Handlers |/////////////////////
  /////////////////////|///////////////////|////////////////////
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
    filePath = filePath.trim();
    
    // Remove enclosing double quotes if present
    if (filePath.startsWith("\"") && filePath.endsWith("\"")) {
        filePath = filePath.substring(1, filePath.length() - 1);
    }

    File file = new File(filePath.trim());
    if (!file.exists()) {
      return "Arquivo não encontrado: \"" + filePath + "\"";
    }
    if (file.isDirectory()) {
      return String.format("\"%s\" é um diretório.", filePath);
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

          if (group != null && CHECK_IF_USER_IS_IN_GROUP_BEFORE  && !rabbit.isUserInGroup(group, username)) {
            terminal.print("Não da pra enviar \"" + file.getName() +
                           "\", pois você não está no grupo: \"" + group +
                           "\".\n\r");
          } else {
            rabbit.sendChunkedRetryingFileMessage(file, username, recipient,
                                                  group, terminal);
          }
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

    return ("Enviando \"" + filePath + "\" para \"" + destination + "\".");
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
    if(GUI) {
      terminal.print(sb.toString());
    } else {
      System.out.println(sb.toString());
      System.out.flush();
    }
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
        .append(pad + ("'Arrow' up or down to move through history.\n\r"))
        .append(pad + "'Ctrl+W' delete a word.\n\r")
        .append(pad + ("'TAB' to autocomplete (commands, filepaths, users, "
                       + "groups, etc ...)."));
    return sb.toString();
  }

  /////////////////////|////////////////|//////////////////////
  /////////////////////|  AutoComplete  |/////////////////////
  /////////////////////|////////////////|////////////////////

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
    // DONE: Cache this
    if (usersChat == null) {
      usersChat = rabbit.apiGetAllQueues();
    }
    List<String> possibleCompletion = new ArrayList<String>();
    for (String users : usersChat) {
      if ((prefix + users).startsWith(currentText)) {
        possibleCompletion.add(prefix + users);
      }
    }
    return possibleCompletion;
  }

  private static List<String> completeGroupsChat(String currentText,
                                                 String prefix) {
    // DONE: Cache this
    if (groupsChat == null) {
      groupsChat = rabbit.apiGetExchangers();
    }
    List<String> possibleCompletion = new ArrayList<String>();
    for (String groups : groupsChat) {
      if ((prefix + groups).startsWith(currentText)) {
        possibleCompletion.add(prefix + groups);
      }
    }
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

  /////////////////////|///////////////|//////////////////////
  /////////////////////| Chat Commands |/////////////////////
  /////////////////////|///////////////|////////////////////

  private static final Command[] COMMANDS = {
    new Command(){
      public String getName(){
        return "addGroup";
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
    },
    new Command() {
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
    },
    new Command() {
      public String getName() { return "whoami"; }

      public String getHelp() { return "!whoami: Prints your own name"; }

      public String execute(String[] parts) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(username);
        return sb.toString();
      }
    },
    new Command() {
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
    },
    new Command() {
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
    },
    new Command() {
      public String getName() { return "upload"; }

      public String getHelp() {
        return "!upload <filepaths...>: send files to either group or user";
      }

      public String execute(String[] parts) throws IOException {
        StringBuilder sb = new StringBuilder();
        StringBuilder filepath = new StringBuilder();
        if (parts.length > 1) {
          for (int i = 1; i < parts.length; i++) {
            filepath.append(parts[i] + " ");
          }
          sb.append(handleUploadCommand("\"" + filepath.toString().trim() + "\""));
        } else {
          sb.append("Usage: !upload <filepath>");
        }
        return sb.toString();
      }
    },
    new Command() {
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
    },
    new Command() {
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
    },
    new Command() {
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
    },

    new Command() {

      public String getName() { return "changeUser"; }

      public String getHelp() { return "!changeUser userName"; }

      public String execute(String[] parts) throws IOException {
        StringBuilder sb = new StringBuilder();
         
        if (parts.length == 2) {
          String input = parts[1];
          if (!isValidUsername(input)) {
            sb.append(USER_NAME_HELP);
          } else {
            username = input;
            rabbit.createUser(username);
            sb.append("Changed user sucessfully to " + username);
          }
        } else {
          sb.append("Usage: !changeUser userName");
        }
        return sb.toString();
      }
    },
    new Command() {
      public String getName() { return "help"; }

      public String getHelp() { return "!help: Displays this help me"; }

      public String execute(String[] parts) throws IOException {
        return helpMenuString();
      }
    },
    new Command() {
      public String getName() { return "host"; }

      public String getHelp() { return "!host: Displays the host"; }

      public String execute(String[] parts) throws IOException {
        return rabbit.getHost();
      }
    }
  };
}
