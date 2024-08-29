import com.rabbitmq.client.AMQP.Exchange.DeclareOk;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

// import java.util.concurrent.atomic.AtomicInteger;

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

  private static Channel defaultChannel = null;
  private static Channel fileChannel = null;
  private static Channel textChannel = null;
  private static Connection connection = null;

  private static String username;
  private static String recipient;
  private static String group;

  private static final String[] HOSTS = {RABBITMQ_HOST, "localhost"};
  private static String currentHost = HOSTS[0];

  private static final int CONNECTION_TIMEOUT = 5000;
  private static final String FILE_DEFAULT_FOLDER = "downloads";
  private static final String FILE_TRANSFER_PREFIX = "file_transfer@";

  // Some MB
  private static final long FILE_MAX_CHUNK_SIZE = 20L * 1024L * 1024L;
  // private static final long FILE_MAX_CHUNK_SIZE = 50L * 1024L * 1024L;
  // private static final long FILE_MAX_CHUNK_SIZE = 50L * 1024L;

  private static final ExecutorService executor =
      Executors.newCachedThreadPool();

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
      String info = createGroup(groupName);
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
      String info = removeGroup(parts[1]);
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
        String msg = addUserToGroup(parts[parts.length - 1], parts[i]);
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
        String msg = removeUserFromGroup(parts[parts.length - 1], parts[i]);
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
  List<String> names = apiGetAllQueues();
  if (names == null || names.size() == 0) {
    // Should never happen
    return "No users exists yet.";
  }
  String commaSeparatedNames = String.join(", ", names);
  return commaSeparatedNames;
}

public static String handleListUsersCommand(String group) {
  if (!groupExists(group)) {
    return "Group \"" + group + "\"does not exist.";
  }

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
  if (file.isDirectory()) {
    return String.format("File \"%s\" is a directory.", filePath);
  }

  String destination = (group != null) ? group : recipient;
  if (destination == null) {
    return ("Entre no prompt de algum grupo ou pessoa para enviar um arquivo.");
  }

  // Start a new thread for file upload
  int maxRetries = 3;
  int retryDelay = 5000; // 5 seconds
  new Thread(() -> {
    boolean success = false;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        // sendChunkedFileMessage(file);
        sendChunkedRetryingFileMessage(file, destination, group != null);
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
private static final int MAX_RETRIES = 3;
private static final int RETRY_DELAY = 5000; // 5 seconds

public static void sendChunkedRetryingFileMessage(File file, String who,
                                                  boolean isGroup)
    throws IOException {
  String mimeType = Files.probeContentType(file.toPath());
  long fileSize = file.length();
  int totalParts = (int)Math.ceil((double)fileSize / FILE_MAX_CHUNK_SIZE);

  if (fileSize > FILE_MAX_CHUNK_SIZE) {

    terminal.print(String.format(
        "O arquivo \"%s\" é grande. Ele será enviado em '%d' partes.\n\r",
        file.getName(), totalParts));
  }

  for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    try (InputStream inputStream = new FileInputStream(file)) {
      byte[] buffer = new byte[(int)Math.min(FILE_MAX_CHUNK_SIZE, fileSize)];
      int bytesRead;
      int partNumber = 1;

      while ((bytesRead = inputStream.read(buffer)) != -1) {

        String metadataString =
            String.format("%s@%d/%d", mimeType, partNumber, totalParts);
        byte[] msg = MessageUtils.createBytesMessage(
            username, group, buffer, bytesRead, metadataString, file.getName());

        String destination = null;
        boolean sent = false;
        for (int innerAttempt = 1; innerAttempt <= MAX_RETRIES;
             innerAttempt++) {
          try {
            if (isGroup) {
              sendGroupMessage(fileChannel, FILE_TRANSFER_PREFIX + who, msg);
              destination = '#' + who;
            } else if (recipient != null) {
              sendPrivateMessage(fileChannel, FILE_TRANSFER_PREFIX + who, msg);
              destination = '@' + who;
            } else {
              terminal.print(
                  "Nenhum destinatário ou grupo selecionado. Por favor, "
                  + "selecione um destinatário ou grupo primeiro.\n\r");
              return;
            }
            sent = true;

            break;
          } catch (IOException e) {

            if (innerAttempt == MAX_RETRIES) {
              throw e;
            }

            terminal.print(
                String.format("Erro ao enviar parte %d/%d. Tentativa %d/%d. "
                                  + "Retentando em %d segundos...\n\r",
                              partNumber, totalParts, innerAttempt, MAX_RETRIES,
                              RETRY_DELAY / 1000));
            Thread.sleep(RETRY_DELAY);
          }
        }

        if (!sent) {
          throw new IOException(
              "Falha ao enviar parte após múltiplas tentativas.");
        }

        if (totalParts == 1) {

          terminal.print(
              String.format("Arquivo \"%s\" foi enviado para %s.\n\r",

                            file.getName(), destination));
        } else {
          terminal.print(String.format(
              "Parte %d/%d do arquivo \"%s\" foi enviada para %s.\n\r",
              partNumber, totalParts, file.getName(), destination));
        }
        partNumber++;
      }
      // If we've made it here, the entire file was sent successfully
      return;
    } catch (IOException | InterruptedException e) {
      if (attempt == MAX_RETRIES) {
        terminal.print(String.format("Erro ao enviar o arquivo \"%s\": %s\n\r",
                                     file.getName(), e.getMessage()));
        throw new IOException(
            "Falha ao enviar arquivo após múltiplas tentativas.", e);
      }
      terminal.print(String.format(
          "Tentativa %d/%d falhou. Retentando em %d segundos...\n\r", attempt,
          MAX_RETRIES, RETRY_DELAY / 1000));
      try {
        Thread.sleep(RETRY_DELAY);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new IOException(
            "Interrompido durante a espera para nova tentativa", ie);
      }
    }
  }
}

public static void sendChunkedFileMessage(File file) throws IOException {
  String mimeType = Files.probeContentType(file.toPath());
  long fileSize = file.length();
  int totalParts = (int)Math.ceil((double)fileSize / FILE_MAX_CHUNK_SIZE);

  if (fileSize > FILE_MAX_CHUNK_SIZE) {

    terminal.print(String.format(
        "O arquivo \"%s\" é grande. Ele será enviado em '%d' partes.\n\r",
        file.getName(), totalParts));
  }

  try (InputStream inputStream = new FileInputStream(file)) {
    byte[] buffer = new byte[(int)Math.min(FILE_MAX_CHUNK_SIZE, fileSize)];
    int bytesRead;
    int partNumber = 1;

    while ((bytesRead = inputStream.read(buffer)) != -1) {
      String metadataString =
          String.format("%s@%d/%d", mimeType, partNumber, totalParts);
      byte[] msg = MessageUtils.createBytesMessage(
          username, group, buffer, bytesRead, metadataString, file.getName());
      String destination = null;

      if (group != null) {
        sendGroupMessage(fileChannel, FILE_TRANSFER_PREFIX + group, msg);
        destination = '#' + group;
      } else if (recipient != null) {
        String whereTo = FILE_TRANSFER_PREFIX + recipient;
        sendPrivateMessage(fileChannel, whereTo, msg);
        destination = '@' + recipient;
      } else {
        terminal.print("Nenhum destinatário ou grupo selecionado. Por favor, "
                       + "selecione um destinatário ou grupo primeiro.\n\r");
        break;
      }

      if (totalParts == 1) {
        terminal.print(String.format("Arquivo \" %s\" foi enviado para %s.\n\r",
                                     file.toString(), destination));
      } else {
        terminal.print(String.format(
            "Parte %d/%d do arquivo \"%s\" foi enviada para %s.\n\r",
            partNumber, totalParts, file.getName(), destination));
      }
      partNumber++;
    }
  } catch (IOException e) {
    terminal.print(String.format("Erro ao enviar o arquivo \"%s\": %s\n\r",
                                 file.getName(), e.getMessage()));
  }
}

public static void sendFileMessage(File file) throws IOException {
  String mimeType = Files.probeContentType(file.toPath());
  byte[] msg = MessageUtils.createFileMessage(username, group, file, mimeType);

  String sentTo = null;
  if (group != null) {
    sendGroupMessage(fileChannel, FILE_TRANSFER_PREFIX + group, msg);
    sentTo = "#" + group;
  } else if (recipient != null) {
    // Send to individual recipient
    String whereTo = FILE_TRANSFER_PREFIX + recipient;
    sendPrivateMessage(fileChannel, whereTo, msg);
    sentTo = "@" + recipient;
  } else {
    terminal.print("No recipient or group selected. Please select a "
                   + "recipient or group first.\n\r");
    return;
  }
  terminal.print(String.format("Arquivo \" %s\" foi enviado para %s.\n\r",
                               file.toString(), sentTo));
}

public static void declareFileTransferQueue(String username)
    throws IOException {
  String queueName = FILE_TRANSFER_PREFIX + username;
  declareQueue(queueName, false, false, false);
}

public static void declareUserQueue(String username) throws IOException {
  declareQueue(username, false, false, false);
}

// NOTE: If we change any of the properties of a queue
// that is already created that triggers a exception.
// TODO: Maybe we need to handle that, should we care?
//

public static void declareQueue(String queue, boolean durable,
                                boolean exclusive, boolean autoDelete)
    throws IOException {
  // If the queue already exists we don't wanna cause an execption for
  // having different properties or something like that
  if (!queueExists(queue)) {
    defaultChannel.queueDeclare(queue, durable, exclusive, autoDelete, null);
  }
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
  for (String users : apiGetAllQueues()) {
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
  for (String groups : apiGetExchangers()) {
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

public static void sendTextMessage(String text) {
  // Submit vs. Execute:
  // submit() if you need a Future to track task completion or handle
  // exceptions. execute() if you just want to run the task without tracking it.
  executor.execute(() -> {
    try {
      byte[] msg =
          MessageUtils.createTextMessage(username, group, text, "text/plain");

      if (group != null) {
        sendGroupMessage(textChannel, group, msg);
      } else if (recipient != null) {
        sendPrivateMessage(textChannel, recipient, msg);
      }
    } catch (IOException e) {
      terminal.print("'sendTextMessage': Could not send text message. \n\r");
      if (DEBUG) {
        e.printStackTrace();
      }
    }
  });
}

public static void sendBlockingTextMessage(String text) throws IOException {
  byte[] msg =
      MessageUtils.createTextMessage(username, group, text, "text/plain");
  if (group != null) {
    // Assumes that we're setting group to null everytime we change to recepient
    // mode
    sendGroupMessage(textChannel, group, msg);
  } else if (recipient != null) {
    // Handle direct messages
    sendPrivateMessage(textChannel, recipient, msg);
  }
}

public static void createUser(String username) throws IOException {
  declareUserQueue(username);
  declareFileTransferQueue(username);
}

public static String createGroup(String groupName) throws IOException {
  StringBuilder sb = new StringBuilder();
  if (groupExists(groupName)) {
    sb.append("Group '" + groupName + "' already exists.");
  } else {
    DeclareOk _ok = defaultChannel.exchangeDeclare(groupName, "fanout", true);
    // Create the file tranfer exchanger as well and bind both
    _ok = defaultChannel.exchangeDeclare(FILE_TRANSFER_PREFIX + groupName,
                                         "fanout", true);
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
    terminal.print("ERROR: Channel already closed " + e.getMessage());
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

// Warn, this function is a bit slow because we create a temporary channel
// everytime
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
      // We check if queue exist, and assume that the file transfer queue also
      // exists NOTE: is this right?
      if (queueExists(queueName)) {
        defaultChannel.queueBind(queueName, groupName, "");
        // Also bind to the file queue to the file exchager
        defaultChannel.queueBind(FILE_TRANSFER_PREFIX + queueName,
                                 FILE_TRANSFER_PREFIX + groupName, "");
        return ("User '" + userName + "' added to group '" + groupName + "'.");
      } else {
        return ("User '" + userName + "' does not exist.");
      }
    } else {
      return ("Tried to add '" + userName + "' to nonexistent group called '" +
              groupName + "'.");
    }
  } catch (IOException e) {
    return ("Error adding user '" + userName + "' to group '" + groupName +
            "'" + e.getMessage() + e.getCause());
  }
}

public static void sendGroupMessage(Channel channel, String groupName,
                                    byte[] message) throws IOException {
  channel.basicPublish(groupName, "", null, message);
}

public static void sendPrivateMessage(Channel channel, String who,
                                      byte[] message) throws IOException {
  channel.basicPublish("", who, null, message);
}

public static Connection tryConnection(ConnectionFactory factory)
    throws IOException, TimeoutException {
  Thread loadingThread = new Thread(() -> {
    String[] animationFrames = {"|", "/", "-", "\\"};
    int i = 0;

    while (!Thread.currentThread().isInterrupted()) {

      System.out.print("\033[" + 9999 + ";1H" +
                       PromptTerminal.MOVE_TO_START_AND_CLEAR + "\r" +
                       animationFrames[i++ % animationFrames.length] +
                       " Connecting  to " + currentHost);
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  });

  int hostIndex = 0;

  loadingThread.start();
  factory.setConnectionTimeout(CONNECTION_TIMEOUT);
  factory.setHandshakeTimeout(CONNECTION_TIMEOUT);

  factory.setAutomaticRecoveryEnabled(true);
  factory.setNetworkRecoveryInterval(10000); // 10 seconds

  factory.setRequestedHeartbeat(60);

  Connection connection = null;
  while (connection == null) {
    currentHost = HOSTS[hostIndex];

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
      defaultChannel.exchangeDelete(groupName);
      return ("Group '" + groupName + "' removed.");
    }
    return ("Group '" + groupName + "' does not exist.");

  } catch (IOException e) {
    return String.format("Error when removing group '%s': %\n\rCause: %s",
                         groupName, e.getMessage(), e.getCause());
  }
}

private static boolean isUserBoundToGroup(String groupName, String username) {
  return true;
}

private static String removeUserFromGroup(String groupName, String user) {
  try {
    if (groupExists(groupName)) {
      if (queueExists(user)) {
        defaultChannel.queueUnbind(user, groupName, "");
        defaultChannel.queueUnbind(FILE_TRANSFER_PREFIX + user,
                                   FILE_TRANSFER_PREFIX + groupName, "");
        return String.format("User '%s' removed from group '%s'", user,
                             groupName);
      } else {
        return String.format("User '%s' doesn't exist '%s'", user, groupName);
      }
    }
    return String.format(
        "Tried to removed user '%s' from nonexistent group '%s'", user,
        groupName);
  } catch (IOException e) {
    return String.format("Erro ao remover %s do grupo %s: %s", user, groupName,
                         e.getMessage());
  }
}

public static void main(String[] args) throws IOException, TimeoutException {

  terminal.clear();

  ConnectionFactory factory = new ConnectionFactory();
  factory.setUsername(RABBITMQ_USERNAME);
  factory.setPassword(RABBITMQ_PASSWORD);
  factory.setVirtualHost("/");

  Thread receiveTextThread = new Thread(ChatClient::receiveText);
  Thread receiveFileThread = new Thread(ChatClient::receiveFiles);

  try {
    connection = tryConnection(factory);
    defaultChannel = connection.createChannel();
    fileChannel = connection.createChannel();
    textChannel = connection.createChannel();

    System.out.println("Raw mode enabled.\n\r"
                       + "Press 'Crtl+c' once to clear and again to quit.\n\r"
                       + "Type '!help' to see available commands after "
                       + "entering your username.\n\r"
                       + "Use 'TAB' to autocomplete.");

    terminal.toRawMode();

    terminal.setPrompt("User: ");
    username = terminal.readLine();
    while (!terminal.shouldQuit() && !isValidUsername(username)) {
      System.out.println("Username can't contain spaces, special characters (" +
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
    createUser(username);

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
        String newrecipient = input.substring(1);
        if (!isValidUsername(newrecipient)) {
          System.out.println(
              "Usernames can't contain spaces, special characters (" +
              SPECIAL_CHARS + "),  neither be empty.");
        } else {
          recipient = newrecipient;
          group = null;
        }
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
        if (!input.isEmpty() && input != null) {
          sendTextMessage(input);
        }
      }
    }
    receiveTextThread.interrupt();
    receiveFileThread.interrupt();
  } catch (IOException | InterruptedException | TimeoutException e) {
    System.out.println("\rFailed with expection: " + e);
    e.printStackTrace();
  } finally {
    terminal.toCookedMode();
    executor.shutdown();
    // NOTE(excyber): important to make sure threads doesn't hang
    // closing the connection already closes the channel I think, because
    // it does not hangs even if we ONLY close the connection
    connection.close();
    System.out.println("Goodbye :)");
    if (!DEBUG) {
      // NOTE: This makes not print the exceptions in other threads
      System.exit(0); // Forcefully quit all threads
    }
    receiveTextThread.interrupt();
    receiveFileThread.interrupt();
  }
}

private static boolean isDelimiter(char c) {
  return c == '-' || c == '/' || c == '!' || c == '@' || c == '#' || c == '.' ||
      Character.isWhitespace(c);
}

private static boolean isDelimiter(char c, String ignore) {
  for (char i : ignore.toCharArray()) {
    if (c == i) {
      return false;
    }
  }

  boolean base = c == '-' || c == '/' || c == '!' || c == '@' || c == '#' ||
                 c == '.' || Character.isWhitespace(c);
  return base;
}

// TODO: Maybe put a delay for readmessages, for when you're receiving too many
// messages. sleep(2.0) something like that
private static void receiveText() {
  DeliverCallback deliverCallback = (consumerTag, delivery) -> {
    boolean textReceivedSuccessfully = false;
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

      String group = msg.getGroup();
      sender = msg.getSender() +
               (group != null && !group.isEmpty() ? ("#" + group) : "");
      hour = msg.getHour();
      date = msg.getDate();

      textReceivedSuccessfully = true;
    } catch (Exception e) {
      System.err.println("Failed to deserialize message: " + e.getMessage());
      textReceivedSuccessfully = false;
      return;
    }

    String timestamp = String.format("%s às %s", date, hour);

    terminal.print(
        String.format("(%s) %s diz: %s\n\r", timestamp, sender, msgContent));

    try {
      if (textReceivedSuccessfully) {
        fileChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      } else {
        // Reject the message and requeue it
        fileChannel.basicNack(delivery.getEnvelope().getDeliveryTag(), false,
                              true);
      }
    } catch (IOException | AlreadyClosedException e) {
      // Handle the error, e.g., log it or attempt to recover the channel
      System.err.println("Failed to acknowledge message: " + e.getMessage());
    }
  };

  try {
    fileChannel.basicConsume(username, false, deliverCallback,
                             consumerTag -> {});
  } catch (IOException e) {
    System.out.println("`receiveMessages` forcefully clased probably, here's "
                       + "the stack trace: \n\r\n\r");
    if (DEBUG) {

      e.printStackTrace();
    }
  }
}

private static void receiveText2() {
  DeliverCallback deliverCallback = (consumerTag, delivery) -> {
    boolean textReceivedSuccessfully = false;
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
      sender = msg.getSender() +
               (group != null && !group.isEmpty() ? ("#" + group) : "");
      hour = msg.getHour();
      date = msg.getDate();
      textReceivedSuccessfully = true;
    } catch (Exception e) {
      System.err.println("Failed to deserialize message: " + e.getMessage());
      textReceivedSuccessfully = false;
      return;
    }

    String timestamp = String.format("%s às %s", date, hour);

    terminal.print(
        String.format("(%s) %s diz: %s\n\r", timestamp, sender, msgContent));

    if (textReceivedSuccessfully) {
      fileChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    } else {
      // Reject the message and requeue it
      fileChannel.basicNack(delivery.getEnvelope().getDeliveryTag(), false,
                            true);
    }
  };

  try {
    boolean autoAck = false; // Manual acknowledgment
    textChannel.basicConsume(username, autoAck, deliverCallback,
                             consumerTag -> {});
  } catch (IOException e) {
    System.out.println(
        "`textChannel.basicConsume` forcefully clased probably, here's "
        + "the stack trace: \n\r\n\r");
    if (DEBUG) {
      e.printStackTrace();
    }
  }
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
    String mimeType = null;
    int partNumber = 1;
    int totalParts = 1;
    String group = null;

    // Deserialize the Message object
    MessageProtoBuffer.Message msg;
    try {
      msg = MessageUtils.fromBytes(messageDataBytes);
      if (msg.getSender().equals(username)) {
        // Don't send FILE messages to ourselves
        return;
      }
      MessageProtoBuffer.Content content = msg.getContent();
      fileContentBytes = content.getBody().toByteArray();
      fileName = content.getName().replace(File.separator, "-");
      group = msg.getGroup();

      // Parse the metadata string
      String metadataString = content.getType();
      String[] metadataParts = metadataString.split("@");
      if (metadataParts.length == 2) {
        mimeType = metadataParts[0];

        String[] partInfo = metadataParts[1].split("/");
        if (partInfo.length == 2) {
          partNumber = Integer.parseInt(partInfo[0]);
          totalParts = Integer.parseInt(partInfo[1]);
        }
      }
      sender = msg.getSender() +

               (group != null && !group.isEmpty() ? ("#" + group) : "");
      hour = msg.getHour();
      date = msg.getDate();
    } catch (Exception e) {
      terminal.print("Failed to deserialize message: " + e.getMessage());
      return;
    }

    String timestamp = String.format("%s às %s", date, hour);

    String folderName = (group != null && !group.isEmpty()) ? group : sender;
    folderName = folderName.replace(File.separator, "-");

    File folder = new File(FILE_DEFAULT_FOLDER, folderName);
    if (!folder.exists()) {
      folder.mkdirs();
    }

    boolean includeDateInFilePath = false;

    Path filePath;
    if (includeDateInFilePath) {
      filePath = Paths.get(folder.getPath(),
                           String.format("%s-%s-%s", date, hour, fileName));
    } else {
      filePath = Paths.get(folder.getPath(), String.format("%s", fileName));
    }

    boolean fileSavedSuccessfully = false;

    try {
      // Simple Case
      if (totalParts == 1) {
        assert partNumber == 1;
        boolean ok =
            FileUtils.saveFile(filePath.toString(), fileContentBytes, false);
        if (ok) {
          terminal.print(String.format("(%s) Arquivo \"%s\" recebido de %s.\n",
                                       timestamp, fileName, sender));
          fileSavedSuccessfully = true;
        } else {

          terminal.print(
              String.format("Arquivo \"%s\" recebido de %s mas "
                                + "não conseguimos salvar em disco :( .\n",
                            fileName, sender));
          fileSavedSuccessfully = false;
        }
      } else {
        // More parts
        File partFileDir = new File(filePath + FileUtils.FILE_PART_DIR_SUFFIX +
                                    File.separator);
        if (!partFileDir.exists()) {
          partFileDir.mkdirs();
        }

        String partFilePath =
            new File(partFileDir,
                     String.format(FileUtils.FILE_PART_FORMAT_STRING,
                                   partNumber, totalParts))
                .toString();

        boolean ok = FileUtils.saveFile(partFilePath, fileContentBytes, false);
        if (ok) {

          terminal.print(
              String.format("Parte %d/%d do arquivo \"%s\" recebida de %s.\n\r",
                            partNumber, totalParts, fileName, sender));
          fileSavedSuccessfully = true;
        } else {
          fileSavedSuccessfully = false;
        }

        if (FileUtils.allPartsReceived(partFileDir, totalParts)) {
          boolean okAssemble = FileUtils.assembleFileAndCleanup(
              partFileDir, filePath, totalParts);
          if (!okAssemble) {
            terminal.print(String.format(
                "Waning: Could not delete intermediate directory %s.\n\r",
                partFileDir));
          }
          terminal.print(
              String.format("(%s) Arquivo \"%s\" recebido de %s.\n\r",
                            timestamp, fileName, sender));
        }
      }
    } catch (Exception e) {
      terminal.print("Failed to save file: " + e.getMessage());
    }

    // Ack the message if the file was saved successfully
    if (fileSavedSuccessfully) {
      fileChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    } else {
      // Reject the message and requeue it
      fileChannel.basicNack(delivery.getEnvelope().getDeliveryTag(), false,
                            true);
    }
  };

  try {
    boolean autoAck = false; // Manual acknowledgment
    fileChannel.basicConsume(queueName, autoAck, deliverCallback,
                             consumerTag -> {});
  } catch (IOException e) {
    System.out.println(
        "\n\r`receiveFiles` IO problems. Here's the stack trace: \n\r");
    e.printStackTrace();
  }
}

public static List<String> apiGetAllQueues() {
  String username = RABBITMQ_USERNAME;
  String password = RABBITMQ_PASSWORD;
  // String username = "guest";
  // String password = "guest";
  String vhost = "%2F";
  List<String> names = new ArrayList<String>();

  try {
    String key = "name";
    URL url = new URI("http://" + currentHost + ":" + RABBITMQ_PORT +
                      "/api/queues/" + vhost + "?columns=" + key)
                  .toURL();

    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    conn.setRequestMethod("GET");

    // Basic authentication
    String auth = username + ":" + password;
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
    conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
    conn.setRequestProperty("Content-Type", "application/json");

    BufferedReader in =
        new BufferedReader(new InputStreamReader(conn.getInputStream()));
    String inputLine;
    StringBuilder response = new StringBuilder();

    while ((inputLine = in.readLine()) != null) {
      response.append(inputLine);
    }
    in.close();

    for (String name : jsonExtractFromKey(response.toString(), key)) {
      if (!name.startsWith("amq.") && !name.isEmpty() &&
          !name.startsWith(FILE_TRANSFER_PREFIX)) {
        names.add(name);
      }
    }
    // return new ArrayList<>(Arrays.asList(response.toString()));
    return names;

  } catch (Exception e) {
    if (DEBUG) {
      e.printStackTrace();
    }
    return names;
  }
}

public static List<String> apiGetExchangers() {
  // String username = "guest"; // RabbitMQ username
  // String password = "guest"; // RabbitMQ password
  String username = RABBITMQ_USERNAME;
  String password = RABBITMQ_PASSWORD;
  String vhost = "%2F"; // Virtual host, use "%2F" for the default vhost
  List<String> exchangeNames = new ArrayList<String>();

  String key = "name";
  try {

    URL url = new URI("http://" + currentHost + ":" + RABBITMQ_PORT +
                      "/api/exchanges/" + vhost + "?columns=" + key)
                  .toURL();

    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    conn.setRequestMethod("GET");

    // Set up basic authentication
    String auth = username + ":" + password;
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
    conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
    conn.setRequestProperty("Content-Type", "application/json");

    int responseCode = conn.getResponseCode();
    // System.out.println("Response Code: " + responseCode);

    BufferedReader in =
        new BufferedReader(new InputStreamReader(conn.getInputStream()));
    String inputLine;
    StringBuilder response = new StringBuilder();

    while ((inputLine = in.readLine()) != null) {
      response.append(inputLine);
    }
    in.close();

    for (String name : jsonExtractFromKey(response.toString(), key)) {
      if (!name.startsWith("amq.") && !name.isEmpty() &&
          !name.startsWith(FILE_TRANSFER_PREFIX)) {
        exchangeNames.add(name);
      }
    }
    return exchangeNames;

  } catch (Exception e) {
    if (DEBUG) {
      e.printStackTrace();
    }
    return exchangeNames;
  }
}

public static List<String> apiGetQueuesBoundToExchange(String exchangeName) {
  // String username = "guest";
  // String password = "guest";
  String username = RABBITMQ_USERNAME;
  String password = RABBITMQ_PASSWORD;
  String vhost = "%2F"; // Virtual host, use "%2F" for the default vhost

  try {
    URL url = new URI("http://" + currentHost + ":" + RABBITMQ_PORT +
                      "/api/exchanges/" + vhost + "/" +
                      exchangeName               // get a specific exchanger
                      + "/bindings/source"     // list of bindings
                      + "?columns=destination" // get just the column we need
                      )
                  .toURL();

    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    conn.setRequestMethod("GET");

    // The most basic auth xD
    String auth = username + ":" + password;
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
    conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
    conn.setRequestProperty("Content-Type", "application/json");

    BufferedReader in =
        new BufferedReader(new InputStreamReader(conn.getInputStream()));
    String inputLine;
    StringBuilder response = new StringBuilder();

    while ((inputLine = in.readLine()) != null) {
      response.append(inputLine);
    }
    in.close();

    // Simple parsing to extract queue names
    List<String> queueNames =
        jsonExtractFromKey(response.toString(), "destination");

    return queueNames;

  } catch (Exception e) {
    if (DEBUG) {
      e.printStackTrace();
    }
    return null;
  }
}

private static List<String> jsonExtractFromKey(String jsonResponse,
                                               String key) {
  List<String> names = new ArrayList<>();
  int index = 0;
  String searchFor = "\"" + key + "\"";
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
