import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DNSClient {
    public static void main(String[] args) {
        try {
            // Configuração do servidor DNS e da mensagem de consulta
            String dnsServer = "8.8.8.8"; // Servidor DNS do Google
            int port = 53;
            String domain = "example.com";

            // Construção da mensagem de consulta DNS
            byte[] query = new byte[512];
            query[0] = (byte) 0xaa;
            query[1] = (byte) 0xbb; // ID da consulta
            query[2] = 0x01; // Flags: consulta padrão
            query[5] = 0x01; // QDCOUNT: uma consulta

            // Adiciona o nome do domínio à consulta
            int pos = 12;
            for (String part : domain.split("\\.")) {
                query[pos++] = (byte) part.length();
                for (char c : part.toCharArray()) {
                    query[pos++] = (byte) c;
                }
            }
            query[pos++] = 0x00; // Fim do nome do domínio

            // Tipo de consulta (A) e classe (IN)
            query[pos++] = 0x00;
            query[pos++] = 0x01; // Tipo A
            query[pos++] = 0x00;
            query[pos++] = 0x01; // Classe IN


            // Envio da consulta DNS via UDP
            DatagramSocket socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName(dnsServer);
            DatagramPacket packet = new DatagramPacket(query, pos, address, port);
            socket.send(packet);

            // Recebimento da resposta
            byte[] buffer = new byte[512];
            packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            // Exibição da resposta
            System.out.println("Resposta recebida: ");
            for (int i = 0; i < packet.getLength(); i++) {
                System.out.printf("%02x ", buffer[i]);
            }

            System.out.println();

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }
}
