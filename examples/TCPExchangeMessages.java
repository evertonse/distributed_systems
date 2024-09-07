/**
 * @author Tarcisio da Rocha (Prof. DCOMP/UFS)
 */
package br.ufs.dcomp.ExemploTcpJava;

import java.net.*;
import java.io.*;
public class TCPExchangeMessages {
    public static void main(String[] args){
        
        try {
            System.out.print("[ Iniciando Servidor TCP    .........................  ");
            ServerSocket ss = new ServerSocket(3300, 5, InetAddress.getByName("127.0.0.1"));
            System.out.println("[OK] ]");
            
            System.out.print("[ Aquardando pedidos de conexão    ..................  ");
            Socket sock = ss.accept(); // Operação bloqueante (aguardando pedido de conexão)
            System.out.println("[OK] ]");
            
            InputStream is = sock.getInputStream(); //Canal de entrada de dados
            OutputStream os = sock.getOutputStream(); //Canal de saída de dados
            byte[] buf = new byte[20]; // buffer de recepção

            System.out.print("[ Aguardando recebimento de mensagem   ..............  ");
            is.read(buf); // Operação bloqueante (aguardando chegada de dados)
            System.out.println("[OK] ]");
            
            String msg = new String(buf); // Mapeando vetor de bytes recebido para String
            
            System.out.println("  Mensagem recebida: "+ msg);

            msg = "Hello, from server";
            buf = msg.getBytes();
            os.write(buf);
            readMessagesFromClient(sock);

        }catch(Exception e){System.out.println(e);}
        System.out.println("[ FIM ]");
    }
    
    // Function to continuously read user input and send messages to the server
    private static void sendMessagesToServer(Socket sock) {
        try {
            OutputStream os = sock.getOutputStream(); //output
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String userInput;

            System.out.println("[ Digite suas mensagens (digite 'quit' para encerrar): ]");

            String[] exits = { "quit", "exit" };
            String listen = "done";
            boolean running = true;
            InetAddress localMachine = java.net.InetAddress.getLocalHost();
            String user = System.getProperty("user.name") + "@" + localMachine.getHostName();
            
            while (running) {
                // Read user input from the console
                System.out.print(user + "> ");
                userInput = consoleReader.readLine();

                // Check for exit condition

                for (int i = 0; i < exits.length; i += 1) {
                    if (exits[i].equalsIgnoreCase(userInput)) {
                        System.out.println("Client: Encerrando o cliente...");
                        running = false;
                        break;
                    }
                }

                // Send the message to the server
                os.write((userInput + "\n").getBytes()); // Send the message with a newline
                os.flush(); //IMPORTANT: Ensure the message is sent immediately

                if ("DONE".equalsIgnoreCase(userInput)) {
                    readMessagesFromClient(sock);
                }
                
                // System.out.print("\r\033[K");
                // System.out.print("\033[H");
                // System.out.print("\033c");
                // Move the cursor to the beginning of the line
                // System.out.print("\r"); // Carriage return to the start of the line
                // // Print spaces to clear the line
                // System.out.print("                                                  "); // Overwrite with spaces
                // System.out.print("\r\r"); // Move back to the start of the line again
                // System.out.print("\033[3J");
                System.out.print("\033[2K"); // Clear the line
                System.out.print("\033[F");   // Move the cursor up one line 
                System.out.println(user + ": " + userInput);
            }
        } catch (IOException e) {
            System.out.println("Erro ao enviar mensagem para o servidor: " + e.getMessage());
        }
    }

    private static void readMessagesFromClient(Socket sock) {
        try {
            InputStream is = sock.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String msg;

            while ((msg = reader.readLine()) != null) {
                System.out.println(" them: " + msg);
                if ("DONE".equalsIgnoreCase(msg)) {
                    sendMessagesToServer(sock);
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler mensagem do cliente: " + e.getMessage());
        } finally {
            try {
                sock.close(); // Close the socket when done
            } catch (IOException e) {
                System.out.println(" Erro ao fechar o socket: " + e.getMessage());
            }
        }
    }
    private static void readMessagesFromClient2(Socket sock) {
        try {
            InputStream is = sock.getInputStream(); // Canal de entrada de dados
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String msg;

            System.out.println("[ Server: Aguardando recebimento de mensagens do cliente... ]");
            while ((msg = reader.readLine()) != null) {
                System.out.println(" Server: Mensagem recebida: " + msg);
            }
        } catch (IOException e) {
            System.out.println("Server: Erro ao ler mensagem do cliente: " + e.getMessage());
        } finally {
            try {
                sock.close(); // Close the socket when done
            } catch (IOException e) {
                System.out.println(" Server: Erro ao fechar o socket: " + e.getMessage());
            }
        }
    }
}

