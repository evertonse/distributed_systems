import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.protobuf.ByteString;

public class MessageUtils {

    // Create a new message with the provided details
    public static byte[] createMessage(String sender, String group, String content, String mimeType) {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String hour = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // Build the content of the message
        MessageProtoBuffer.Content contentProto = MessageProtoBuffer.Content.newBuilder()
                .setType(mimeType)
                .setBody(ByteString.copyFromUtf8(content)) // Assuming content is a String
                .build();

        // Build the full message with metadata

        MessageProtoBuffer.Message messageProto = MessageProtoBuffer.Message.newBuilder()
                .setSender(sender == null ? "unkown" : sender)
                .setDate(date)
                .setHour(hour)
                .setGroup(group == null ? "" : group)
                .setContent(contentProto)
                .build();

        // Serialize the message to a byte array
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
