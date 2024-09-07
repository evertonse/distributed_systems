import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDP {

  private static final int PORT = 6969;
  private static final int BUFFER_SIZE = 1024;

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Usage: java UdpServerClient serve | <message>");
      return;
    }

    if (args[0].equals("server")) {
      startServer();
    } else if (args[0].equals("client")) {
      startClient(args[0]);
    } else {

      System.out.println("Usage: java UdpServerClient serve | <message>");
      return;
    }
  }

  private static void startServer() {
    try (DatagramSocket socket = new DatagramSocket(PORT)) {
      System.out.println("UDP Server is running on port " + PORT + "...");

      byte[] buffer = new byte[BUFFER_SIZE];
      while (true) {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet); // Receive packet from client

        String receivedMessage =
            new String(packet.getData(), 0, packet.getLength());
        InetAddress originAddress = packet.getAddress();
        int originPort = packet.getPort();

        System.out.println("\n\r  Received message: " + receivedMessage);
        System.out.println("  Endereço de origem:   " +
                           originAddress.getHostAddress());
        System.out.println("  Porta de origem:      " + originPort);

        // Optionally, send a response back to the client
        String response = "SERVER is beepboping";
        byte[] responseData = response.getBytes();
        DatagramPacket responsePacket =
            new DatagramPacket(responseData, responseData.length,
                               packet.getAddress(), packet.getPort());
        socket.send(responsePacket);
      }
    } catch (Exception e) {

      e.printStackTrace();
    }
  }

  private static void startClient(String message) {
    try (DatagramSocket socket = new DatagramSocket()) {
      InetAddress serverAddress = InetAddress.getByName("localhost");

      // Send message to the server

      byte[] messageData = message.getBytes();
      DatagramPacket packet = new DatagramPacket(
          messageData, messageData.length, serverAddress, PORT);

      socket.send(packet);
      System.out.println("Sent message: " + message);

      // Receive response from the server
      byte[] buffer = new byte[BUFFER_SIZE];
      DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
      socket.receive(responsePacket);
      String response =
          new String(responsePacket.getData(), 0, responsePacket.getLength());
      System.out.println("Received response from server: " + response);
    } catch (Exception e) {

      e.printStackTrace();
    }
  }
}
