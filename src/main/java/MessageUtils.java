import com.google.protobuf.ByteString;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MessageUtils {
  // Create a new message with the provided details
  public static byte[] createTextMessage(
      String sender, String group, String content, String mimeType) {
    LocalDateTime now = LocalDateTime.now();
    String date = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    String hour = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

    // Build the content of the message
    MessageProtoBuffer.Content contentProto =
        MessageProtoBuffer.Content.newBuilder()
            .setType(mimeType)
            .setBody(ByteString.copyFromUtf8(content)) // Assuming
            // content
            // is
            // a
            // String
            .build();

    // Build the full message with metadata
    MessageProtoBuffer.Message messageProto =
        MessageProtoBuffer.Message.newBuilder()
            .setSender(sender == null ? "unkown" : sender)
            .setDate(date)
            .setHour(hour)
            .setGroup(group == null ? "" : group)
            .setContent(contentProto)
            .build();

    // Serialize the message to a byte array
    return messageProto.toByteArray();
  }

  public static byte[] createFileMessage(String sender, String group, File file, String mimeType)
      throws IOException {
    LocalDateTime now = LocalDateTime.now();
    String date = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    String hour = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

    byte[] fileContent = Files.readAllBytes(file.toPath());

    MessageProtoBuffer.Content contentProto =
        MessageProtoBuffer.Content.newBuilder()
            .setType(mimeType)
            .setBody(ByteString.copyFrom(fileContent))
            .setName(file.getName())
            .build();

    MessageProtoBuffer.Message messageProto =
        MessageProtoBuffer.Message.newBuilder()
            .setSender(sender == null ? "unknown" : sender)
            .setDate(date)
            .setHour(hour)
            .setGroup(group == null ? "" : group)
            .setContent(contentProto)
            .build();

    return messageProto.toByteArray();
  }

  public static byte[] createBytesMessage(
      String sender, String group, byte[] bytes, int length, String mimeType, String name)
    throws IOException {
    LocalDateTime now = LocalDateTime.now();
    String date = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    String hour = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

    MessageProtoBuffer.Content contentProto =
        MessageProtoBuffer.Content.newBuilder()
            .setType(mimeType)
            .setBody(ByteString.copyFrom(ByteBuffer.wrap(bytes, 0, length)))
            .setName(name)
            .build();

    MessageProtoBuffer.Message messageProto =
        MessageProtoBuffer.Message.newBuilder()
            .setSender(sender == null ? "unknown" : sender)
            .setDate(date)
            .setHour(hour)
            .setGroup(group == null ? "" : group)
            .setContent(contentProto)
            .build();

    return messageProto.toByteArray();
  }

  // Deserialize a byte array into a Message object
  public static MessageProtoBuffer.Message fromBytes(byte[] messageBytes) {
    try {
      return MessageProtoBuffer.Message.parseFrom(messageBytes);
    } catch (Exception e) {
      System.err.println("ERROR: MessageProtoBuffer failed to parse from bytes");
      System.exit(1);
      return null;
    }
  }
}
