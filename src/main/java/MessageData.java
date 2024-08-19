import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class MessageData implements Serializable {
    private static final long serialVersionUID = 1L; // For serialization compatibility

    private String sender;

    private String messageBody;

    public MessageData(String sender, String messageBody) {
        this.sender = sender;

        this.messageBody = messageBody;
    }

    public String getSender() {
        return sender;

    }

    public String getMessageBody() {
        return messageBody;
    }

    @Override
    public String toString() {
        return "Message{" +
                "sender='" + sender + '\'' +
                ", messageBody='" + messageBody + '\'' +
                '}';
    }

    public byte[] toBytes() throws IOException {

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
            oos.flush();
            return bos.toByteArray();
        }
    }

    public static MessageData fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (MessageData) ois.readObject();
        }
    }

}
