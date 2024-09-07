import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class SimpleDNSResolver {

    // DNS server to use (Google's public DNS server)
    private static final String DNS_SERVER = "8.8.8.8";
    private static final int DNS_PORT = 53;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java SimpleDNSResolver <hostname>");
            return;
        }

        String hostname = args[0];
        try {
            byte[] dnsQuery = createDnsQuery(hostname);
            byte[] response = sendDnsQuery(dnsQuery);

            // Parsing the response to extract the IP address
            InetAddress ipAddress = extractIpAddress(response);

            if (ipAddress != null) {
                System.out.println("IP Address of " + hostname + ": " + ipAddress.getHostAddress());
            } else {
                System.out.println("Could not resolve " + hostname);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] createDnsQuery(String hostname) throws Exception {

        ByteBuffer buffer = ByteBuffer.allocate(512);

        // Transaction ID (random 2 bytes)
        buffer.putShort((short) 0x1234);


        // Flags: standard query, recursion desired
        buffer.putShort((short) 0x0100);

        // Questions: 1, Answer RRs: 0, Authority RRs: 0, Additional RRs: 0
        buffer.putShort((short) 1);  // QDCOUNT
        buffer.putShort((short) 0);  // ANCOUNT
        buffer.putShort((short) 0);  // NSCOUNT
        buffer.putShort((short) 0);  // ARCOUNT

        // Query: split the hostname by dots and add length-prefixed labels
        String[] labels = hostname.split("\\.");

        for (String label : labels) {
            byte[] labelBytes = label.getBytes("UTF-8");
            buffer.put((byte) labelBytes.length);
            buffer.put(labelBytes);
        }

        // End of hostname (null byte)
        buffer.put((byte) 0);

        // Type A (host address)
        buffer.putShort((short) 1);

        // Class IN (internet)
        buffer.putShort((short) 1);

        return buffer.array();

    }

    private static byte[] sendDnsQuery(byte[] dnsQuery) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress dnsServer = InetAddress.getByName(DNS_SERVER);

        // Sending the DNS query packet
        DatagramPacket packet = new DatagramPacket(dnsQuery, dnsQuery.length, dnsServer, DNS_PORT);

        socket.send(packet);

        // Buffer to receive the response
        byte[] response = new byte[512];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        socket.receive(responsePacket);
        socket.close();

        return response;
    }

    private static InetAddress extractIpAddress(byte[] response) throws Exception {
        // DNS response parsing: check answer count and extract IP
        ByteBuffer buffer = ByteBuffer.wrap(response);

        buffer.position(6);
        short answerCount = buffer.getShort();

        // Skip the header and question sections
        buffer.position(12);
        while (buffer.get() != 0) { }  // Skip query name
        buffer.position(buffer.position() + 4); // Skip type and class

        for (int i = 0; i < answerCount; i++) {
            // Skip answer name, type, class, TTL
            buffer.position(buffer.position() + 10);

            // Data length of the answer
            short dataLength = buffer.getShort();

            // If the answer type is A (IPv4 address) and data length is 4
            if (dataLength == 4) {
                byte[] ipBytes = new byte[4];
                buffer.get(ipBytes);
                return InetAddress.getByAddress(ipBytes);
            } else {
                // Skip this answer
                buffer.position(buffer.position() + dataLength);
            }
        }

        // If no valid A record was found
        return null;
    }
}

