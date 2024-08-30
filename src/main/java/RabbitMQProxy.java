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
import java.util.concurrent.TimeoutException;

class Login {
  public String username, password;
}

public class RabbitMQProxy {

  private static final String SPECIAL_CHARS = "!#@-/";
  private Login login;
  private String host = "localhost";
  private String[] possibleHosts = {"localhost"};

  private final String port = "15672";
  private static final boolean DEBUG = true;

  public Channel defaultChannel = null;
  public Channel fileChannel = null;
  public Channel textChannel = null;
  private Connection connection = null;
  private ConnectionFactory factory;

  private static final int CONNECTION_TIMEOUT = 5000;
  private static final String FILE_TRANSFER_PREFIX = "file_transfer@";
  private static final String FILE_DEFAULT_FOLDER = "downloads";

  // Some MB
  private static final long FILE_MAX_CHUNK_SIZE = 20L * 1024L * 1024L;
  // private static final long FILE_MAX_CHUNK_SIZE = 50L * 1024L * 1024L;
  // private static final long FILE_MAX_CHUNK_SIZE = 50L * 1024L;

  private static boolean isValidQueue(String username) {
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

  private static final int MAX_RETRIES = 3;
  private static final int RETRY_DELAY = 5000; // 5 seconds

  public void sendChunkedRetryingFileMessage(File file, String sender,
                                             String userName, String groupName,
                                             Printable printer)
      throws IOException {
    String mimeType = Files.probeContentType(file.toPath());
    long fileSize = file.length();
    int totalParts = (int)Math.ceil((double)fileSize / FILE_MAX_CHUNK_SIZE);

    if (fileSize > FILE_MAX_CHUNK_SIZE) {

      printer.print(String.format(
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
              sender, groupName, buffer, bytesRead, metadataString,
              file.getName());

          String destination = null;
          boolean sent = false;
          for (int innerAttempt = 1; innerAttempt <= MAX_RETRIES;
               innerAttempt++) {
            try {
              if (groupName != null) {
                sendGroupMessage(fileChannel, FILE_TRANSFER_PREFIX + groupName,
                                 msg);
                destination = '#' + groupName;
              } else if (userName != null) {
                sendPrivateMessage(fileChannel, FILE_TRANSFER_PREFIX + userName,
                                   msg);
                destination = '@' + userName;
              } else {
                printer.print(
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

              printer.print(
                  String.format("Erro ao enviar parte %d/%d. Tentativa %d/%d. "
                                    + "Retentando em %d segundos...\n\r",
                                partNumber, totalParts, innerAttempt,
                                MAX_RETRIES, RETRY_DELAY / 1000));
              Thread.sleep(RETRY_DELAY);
            }
          }

          if (!sent) {
            throw new IOException(
                "Falha ao enviar parte após múltiplas tentativas.");
          }

          if (totalParts == 1) {
            printer.print(
                String.format("Arquivo \"%s\" foi enviado para %s.\n\r",
                              file.getName(), destination));
          } else {
            printer.print(String.format(
                "Parte %d/%d do arquivo \"%s\" foi enviada para %s.\n\r",
                partNumber, totalParts, file.getName(), destination));

            if (partNumber == totalParts) {
              printer.print(String.format(
                  "Arquivo \"%s\" foi completamente enviado para %s.\n\r",
                  file.getName(), destination));
            }
          }
          partNumber++;
        }
        // If we've made it here, the entire file was sent successfully
        return;
      } catch (IOException | InterruptedException e) {
        if (attempt == MAX_RETRIES) {
          printer.print(String.format("Erro ao enviar o arquivo \"%s\": %s\n\r",
                                      file.getName(), e.getMessage()));
          throw new IOException(
              "Falha ao enviar arquivo após múltiplas tentativas.", e);
        }
        printer.print(String.format(
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
    return;
  }

  public boolean sendTextMessage(String sender, String userName,
                                 String groupName, String text) {
    try {
      byte[] msg =
          MessageUtils.createTextMessage(sender, groupName, text, "text/plain");

      if (groupName != null) {
        sendGroupMessage(textChannel, groupName, msg);
      } else if (userName != null) {
        sendPrivateMessage(textChannel, userName, msg);
      }
    } catch (IOException e) {
      if (DEBUG) {
        e.printStackTrace();
      }
      return false;
    }
    return true;
  }

  public void declareFileTransferQueue(String userName) throws IOException {
    String queueName = FILE_TRANSFER_PREFIX + userName;
    declareQueue(queueName, false, false, false);
  }

  public void declareUserQueue(String userName) throws IOException {
    declareQueue(userName, false, false, false);
  }

  // NOTE: If we change any of the properties of a queue
  // that is already created that triggers a exception.
  // TODO: Maybe we need to handle that, should we care?
  //
  public void declareQueue(String queue, boolean durable, boolean exclusive,
                           boolean autoDelete) throws IOException {
    // If the queue already exists we don't wanna cause an execption for
    // having different properties or something like that
    if (!queueExists(queue)) {
      defaultChannel.queueDeclare(queue, durable, exclusive, autoDelete, null);
    }
  }

  public void createUser(String userName) throws IOException {
    declareUserQueue(userName);
    declareFileTransferQueue(userName);
  }

  public String createGroup(String creator, String groupName)
      throws IOException {
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
    String info = addUserToGroup(groupName, creator);
    sb.append("\n\r" + info);
    return sb.toString();
  }

  public boolean groupExists(String groupName) {
    // Using a Temporary channel
    // see: https://groups.google.com/g/rabbitmq-users/c/ZTfVwe_HYXc
    try (Channel tempChannel = connection.createChannel()) {
      tempChannel.exchangeDeclarePassive(groupName);
      return true;
    } catch (AlreadyClosedException e) {
      return false;
    } catch (Exception e) {
      // The exception indicates that the exchange doesn't exist or another
      // issue occurred
      return false;
    }
  }

  // https://stackoverflow.com/questions/3457305/how-can-i-check-whether-a-rabbitmq-message-queue-exists-or-not
  // queue.declare is an idempotent operation. So, if you run it once, twice, N
  // times, the result will still be the same.
  // If you want to ensure that the queue exists, just declare it before using
  // it. Make sure you declare it with the same durability, exclusivity,
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
  public boolean queueExists(String queueName) {
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

  public String addUserToGroup(String groupName, String userName) {
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
          return ("User '" + userName + "' added to group '" + groupName +
                  "'.");
        } else {
          return ("User '" + userName + "' does not exist.");
        }
      } else {
        return ("Tried to add '" + userName +
                "' to nonexistent group called '" + groupName + "'.");
      }
    } catch (IOException e) {
      return ("Error adding user '" + userName + "' to group '" + groupName +
              "'" + e.getMessage() + e.getCause());
    }
  }

  public void sendGroupMessage(Channel channel, String groupName,
                               byte[] message) throws IOException {
    channel.basicPublish(groupName, "", null, message);
  }

  public void sendPrivateMessage(Channel channel, String who, byte[] message)
      throws IOException {
    channel.basicPublish("", who, null, message);
  }

  public Connection tryConnection() throws IOException, TimeoutException {
    Thread loadingThread = new Thread(() -> {
      String[] animationFrames = {"|", "/", "-", "\\"};
      int i = 0;

      while (!Thread.currentThread().isInterrupted()) {

        System.out.print("\033[" + 9999 + ";1H" +
                         PromptTerminal.MOVE_TO_START_AND_CLEAR + "\r" +
                         animationFrames[i++ % animationFrames.length] +
                         " Connecting  to " + host);
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
      host = possibleHosts[hostIndex];

      factory.setHost(host);
      try {
        Thread.sleep(400); // initial wait just to show animation
        try {
          connection = factory.newConnection();
        } catch (Exception e) {
          System.out.println("\nSwitching to the next host...");
        }
      } catch (Exception e) {
        System.out.println("\r\n\rFailed to connect to " + host + ".");
      }

      // Switch to the next host
      hostIndex = (hostIndex + 1) % possibleHosts.length;
    }

    loadingThread.interrupt();
    System.out.println("\n\rConnected to " + host + "!");
    return connection;
  }

  public String removeGroup(String groupName) {
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

  public String removeUserFromGroup(String groupName, String user) {
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
      return String.format("Erro ao remover %s do grupo %s: %s", user,
                           groupName, e.getMessage());
    }
  }

  public RabbitMQProxy(String username, String password, String[] possibleHosts)
      throws IOException, TimeoutException {
    this.login = new Login();
    this.login.username = username;
    this.login.password = password;
    this.possibleHosts = possibleHosts;
    this.host = possibleHosts.length > 0 ? possibleHosts[0] : "localhost";

    factory = new ConnectionFactory();
    factory.setUsername(username);
    factory.setPassword(password);
    factory.setVirtualHost("/");
    factory.setConnectionTimeout(CONNECTION_TIMEOUT);
    factory.setHandshakeTimeout(CONNECTION_TIMEOUT);
    factory.setAutomaticRecoveryEnabled(true);
    factory.setNetworkRecoveryInterval(10000); // 10 seconds
    factory.setRequestedHeartbeat(60);

    connection = this.tryConnection();
    defaultChannel = connection.createChannel();
    fileChannel = connection.createChannel();
    textChannel = connection.createChannel();
  }

  public void shutdown() throws IOException { connection.close(); }

  public List<String> apiGetAllQueues() {
    // String username = "guest";
    // String password = "guest";
    String vhost = "%2F";
    List<String> names = new ArrayList<String>();

    try {
      String key = "name";
      URL url = new URI("http://" + host + ":" + port + "/api/queues/" + vhost +
                        "?columns=" + key)
                    .toURL();

      HttpURLConnection conn = (HttpURLConnection)url.openConnection();
      conn.setRequestMethod("GET");

      // Basic authentication
      String auth = login.username + ":" + login.password;
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

  public List<String> apiGetExchangers() {
    // String username = "guest"; // RabbitMQ username
    // String password = "guest"; // RabbitMQ password
    String vhost = "%2F"; // Virtual host, use "%2F" for the default vhost
    List<String> exchangeNames = new ArrayList<String>();

    String key = "name";
    try {

      URL url = new URI("http://" + host + ":" + port + "/api/exchanges/" +
                        vhost + "?columns=" + key)
                    .toURL();

      HttpURLConnection conn = (HttpURLConnection)url.openConnection();
      conn.setRequestMethod("GET");

      // Set up basic authentication
      String auth = login.username + ":" + login.password;
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

  public List<String> apiGetQueuesBoundToExchange(String exchangeName) {
    // String username = "guest";
    // String password = "guest";
    String vhost = "%2F"; // Virtual host, use "%2F" for the default vhost

    try {
      URL url =
          new URI("http://" + host + ":" + port + "/api/exchanges/" + vhost +
                  "/" + exchangeName       // get a specific exchanger
                  + "/bindings/source"     // list of bindings
                  + "?columns=destination" // get just the column we need
                  )
              .toURL();

      HttpURLConnection conn = (HttpURLConnection)url.openConnection();
      conn.setRequestMethod("GET");

      // The most basic auth xD
      String auth = login.username + ":" + login.password;
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

  private List<String> jsonExtractFromKey(String jsonResponse, String key) {
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

  public void receiveText(String userName, Printable printer) {
    StringBuilder sb = new StringBuilder();
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

      printer.print(
          String.format("(%s) %s diz: %s\n\r", timestamp, sender, msgContent));

      try {
        if (textReceivedSuccessfully) {
          textChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        } else {
          // Reject the message and requeue it
          textChannel.basicNack(delivery.getEnvelope().getDeliveryTag(), false,
                                true);
        }
      } catch (IOException e) {
        // Handle the error, e.g., log it or attempt to recover the channel
        printer.print("Failed to acknowledge message: " + e.getMessage());
      }
    };

    try {
      textChannel.basicConsume(userName, false, deliverCallback,
                               consumerTag -> {});
      return;
    } catch (IOException e) {
      if (DEBUG) {
        e.printStackTrace();
      }
      printer.print(
          "Problems in `receiveText` the stack is printed above. \n\r\n\r");
    }
  }

  public void receiveFiles(String userName, Printable printer) {
    String queueName = FILE_TRANSFER_PREFIX + userName;
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
        if (msg.getSender().equals(userName)) {
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
        printer.print("Failed to deserialize message: " + e.getMessage() +
                      ".\n\r");
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
            printer.print(
                String.format("(%s) Arquivo \"%s\" recebido de %s.\n\r",
                              timestamp, fileName, sender));
            fileSavedSuccessfully = true;
          } else {

            printer.print(
                String.format("Arquivo \"%s\" recebido de %s mas "
                                  + "não conseguimos salvar em disco :( .\n\r",
                              fileName, sender));
            fileSavedSuccessfully = false;
          }
        } else {
          // More parts
          File partFileDir = new File(
              filePath + FileUtils.FILE_PART_DIR_SUFFIX + File.separator);
          if (!partFileDir.exists()) {
            partFileDir.mkdirs();
          }

          String partFilePath =
              new File(partFileDir,
                       String.format(FileUtils.FILE_PART_FORMAT_STRING,
                                     partNumber, totalParts))
                  .toString();

          boolean ok =
              FileUtils.saveFile(partFilePath, fileContentBytes, false);
          if (ok) {

            printer.print(String.format(
                "Parte %d/%d do arquivo \"%s\" recebida de %s.\n\r", partNumber,
                totalParts, fileName, sender));
            fileSavedSuccessfully = true;
          } else {
            fileSavedSuccessfully = false;
          }

          if (FileUtils.allPartsReceived(partFileDir, totalParts)) {
            printer.print(String.format(
                "Juntando %d partes do arquivo \"%s\" em disco.\n\r",
                totalParts, fileName));
            boolean okAssemble = FileUtils.assembleFileAndCleanup(
                partFileDir, filePath, totalParts);
            if (!okAssemble) {
              printer.print(String.format(
                  "Waning: Could not delete intermediate directory %s.\n\r",
                  partFileDir));
            }
            printer.print(
                String.format("(%s) Arquivo \"%s\" recebido de %s.\n\r",
                              timestamp, fileName, sender));
          }
        }
      } catch (Exception e) {
        printer.print("Failed to save file: " + e.getMessage());
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
      if (DEBUG) {
        e.printStackTrace();
      }
      printer.print(
          "IO Problems in `receiveFiles` the stack is printed above. \n\r\n\r");
    }
  }
}
