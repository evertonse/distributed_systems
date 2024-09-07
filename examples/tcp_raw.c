#if 0
self="$(realpath "$0")"
exe="${self%.*}.bin"  # Remove any extension and add .bin

exec_line="gcc \"$self\" -o \"$exe\" -lglut -ldl -Isrc/ -Idemos/ -Ivendor/ -Ivendor/glad/include/ -Ivendor/stb/include/ -Ivendor/noise/ -O3 -Ofast -Wno-write-strings -fomit-frame-pointer -flto"
echo "exec: $exec_line"

eval $exec_line

if [ $? -eq 0 ]; then
    echo "Compile succeeded: $self --> $exe"
    echo "Running: $exe"
    eval $exe $@
    exit $?
else
    echo "Compile error"
    exit 1
fi

#Use this to visualize sudo tcpdump - i lo - n - A 'tcp and port 8080'

#endif

#include <arpa/inet.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define BUFFER_SIZE 65535
#define PORT 6969

unsigned short checksum(void *b, int len) {
  unsigned short *buf = b;

  unsigned int sum = 0;
  unsigned short result;

  for (sum = 0; len > 1; len -= 2)
    sum += *buf++;
  if (len == 1)
    sum += *(unsigned char *)buf;
  sum = (sum >> 16) + (sum & 0xFFFF);
  sum += (sum >> 16);
  result = ~sum;
  return result;
}

void log_tcp_info(struct iphdr *ip_header, struct tcphdr *tcp_header,
                  int payload_size) {
  printf("Received TCP Segment:\n");
  printf("Source IP: %s\n", inet_ntoa(*(struct in_addr *)&ip_header->saddr));
  printf("Destination IP: %s\n",
         inet_ntoa(*(struct in_addr *)&ip_header->daddr));
  printf("Source Port: %u\n", ntohs(tcp_header->source));
  printf("Destination Port: %u\n", ntohs(tcp_header->dest));
  printf("Sequence Number: %u\n", ntohl(tcp_header->seq));
  printf("Acknowledgment Number: %u\n", ntohl(tcp_header->ack_seq));
  printf("Payload Size: %d bytes\n", payload_size);

  printf("--------------------------------------------------\n");
}
void usage() {
  printf("usage: stream|server|raw\n");
}

int server();
int client();
int client_raw_looping();
int client_stream();

int main(int argc, const char **const argv) {
  if (argc <= 1 || (strcmp(argv[1], "server") == 0)) {
    server();
  } else {
    if (strcmp(argv[1], "raw") == 0) {
      client_raw_looping();

    } else if (strcmp(argv[1], "stream") == 0) {
      client_stream();
    }
    goto quit;
  }

quit:
    usage();
    return (EXIT_FAILURE);
}

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <arpa/inet.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <unistd.h>

#define BUFFER_SIZE 65535

int server() {
    int sockfd;
    struct sockaddr_in server_addr, client_addr;
    char buffer[BUFFER_SIZE];
    socklen_t addr_len = sizeof(client_addr);


    // Create raw socket
    sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_TCP);
    if (sockfd < 0) {
        perror("Socket creation failed");
        exit(EXIT_FAILURE);
    }

    // Set up server address
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;
    server_addr.sin_port = htons(PORT);

    // Bind the socket
    if (bind(sockfd, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("Bind failed");
        close(sockfd);
        exit(EXIT_FAILURE);
    }

    printf("Server is listening on port %d\n", PORT);

    while (1) {
        // Receive packet
        int bytes_received = recvfrom(sockfd, buffer, BUFFER_SIZE, 0,
                                      (struct sockaddr *)&client_addr, &addr_len);
        if (bytes_received < 0) {
            perror("Receive failed");
            continue;
        }

        struct iphdr *ip_header = (struct iphdr *)buffer;
        struct tcphdr *tcp_header = (struct tcphdr *)(buffer + (ip_header->ihl * 4));

        // Check for SYN packet
        if (tcp_header->syn == 1 && tcp_header->ack == 0) {
            printf("Received SYN from client\n");


            // Prepare SYN-ACK packet
            char packet[BUFFER_SIZE];
            memset(packet, 0, BUFFER_SIZE);

            // Fill IP header
            struct iphdr *ip_hdr = (struct iphdr *)packet;
            ip_hdr->version = 4;
            ip_hdr->ihl = 5;
            ip_hdr->tot_len = sizeof(struct iphdr) + sizeof(struct tcphdr);
            ip_hdr->protocol = IPPROTO_TCP;
            ip_hdr->saddr = server_addr.sin_addr.s_addr;

            ip_hdr->daddr = client_addr.sin_addr.s_addr;

            // Fill TCP header
            struct tcphdr *tcp_hdr = (struct tcphdr *)(packet + sizeof(struct iphdr));
            tcp_hdr->source = htons(PORT);
            tcp_hdr->dest = htons(PORT);
            tcp_hdr->seq = htonl(1); // Initial sequence number
            tcp_hdr->ack_seq = ntohl(tcp_header->seq) + 1; // Acknowledge client's sequence number
            tcp_hdr->doff = 5; // TCP header size
            tcp_hdr->syn = 1; // SYN flag
            tcp_hdr->ack = 1; // ACK flag
            tcp_hdr->check = checksum(tcp_hdr, sizeof(struct tcphdr)); // Calculate checksum


            // Send SYN-ACK packet
            sendto(sockfd, packet, sizeof(struct iphdr) + sizeof(struct tcphdr), 0,
                   (struct sockaddr *)&client_addr, addr_len);
            printf("Sent SYN-ACK to client\n");
        }

        // Check for ACK packet
        if (tcp_header->ack == 1) {
            printf("Received ACK from client\n");
            break; // Handshake complete
        }
    }


    // Loop to read and print payload
    while (1) {

        // Receive data packet
        int bytes_received = recvfrom(sockfd, buffer, BUFFER_SIZE, 0,
                                      (struct sockaddr *)&client_addr, &addr_len);
        if (bytes_received < 0) {
            perror("Receive failed");
            continue;
        }

        struct iphdr *ip_header = (struct iphdr *)buffer;
        struct tcphdr *tcp_header = (struct tcphdr *)(buffer + (ip_header->ihl * 4));

        // Calculate payload size
        int payload_size = bytes_received - (ip_header->ihl * 4) - (tcp_header->doff * 4);
        if (payload_size > 0) {
            // Print the payload
            char *payload = (char *)(buffer + (ip_header->ihl * 4) + (tcp_header->doff * 4));
            printf("Received payload: %.*s\n", payload_size, payload);
        }
    }

    close(sockfd);
    return 0;
}


int server_ack_and_quit() {
  int sockfd;
  struct sockaddr_in server_addr, client_addr;
  char buffer[BUFFER_SIZE];
  socklen_t addr_len = sizeof(client_addr);

  // Create raw socket
  sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_TCP);
  if (sockfd < 0) {
    perror("Socket creation failed");
    exit(EXIT_FAILURE);
  }

  // Set up server address
  memset(&server_addr, 0, sizeof(server_addr));
  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = INADDR_ANY;
  server_addr.sin_port = htons(PORT);

  // Bind the socket

  if (bind(sockfd, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
    perror("Bind failed");

    close(sockfd);
    exit(EXIT_FAILURE);
  }

  printf("Bind: family %d, s_addr %d, port %d \n\r", server_addr.sin_family,
         server_addr.sin_addr.s_addr, server_addr.sin_port);

  printf("Server is listening on port %d\n", PORT);

  while (1) {
    // Receive packet
    int bytes_received = recvfrom(sockfd, buffer, BUFFER_SIZE, 0,
                                  (struct sockaddr *)&client_addr, &addr_len);
    if (bytes_received < 0) {
      perror("Receive failed");
      continue;
    }

    struct iphdr *ip_header = (struct iphdr *)buffer;
    struct tcphdr *tcp_header =
        (struct tcphdr *)(buffer + (ip_header->ihl * 4));

    // Check for SYN packet
    if (tcp_header->syn == 1 && tcp_header->ack == 0) {
      printf("Received SYN from client\n");

      // Prepare SYN-ACK packet
      char packet[BUFFER_SIZE];
      memset(packet, 0, BUFFER_SIZE);

      // Fill IP header
      struct iphdr *ip_hdr = (struct iphdr *)packet;
      ip_hdr->version = 4;
      ip_hdr->ihl = 5;
      ip_hdr->tot_len = sizeof(struct iphdr) + sizeof(struct tcphdr);

      ip_hdr->protocol = IPPROTO_TCP;
      ip_hdr->saddr = server_addr.sin_addr.s_addr;
      ip_hdr->daddr = client_addr.sin_addr.s_addr;

      // Fill TCP header
      struct tcphdr *tcp_hdr = (struct tcphdr *)(packet + sizeof(struct iphdr));

      tcp_hdr->source = htons(PORT);
      tcp_hdr->dest = htons(PORT);
      tcp_hdr->seq = htonl(1); // Initial sequence number
      tcp_hdr->ack_seq =
          ntohl(tcp_header->seq) + 1; // Acknowledge client's sequence number
      tcp_hdr->doff = 5;              // TCP header size
      tcp_hdr->syn = 1;               // SYN flag
      tcp_hdr->ack = 1;               // ACK flag
      tcp_hdr->check =
          checksum(tcp_hdr, sizeof(struct tcphdr)); // Calculate checksum

      // Send SYN-ACK packet
      sendto(sockfd, packet, sizeof(struct iphdr) + sizeof(struct tcphdr), 0,
             (struct sockaddr *)&client_addr, addr_len);
      printf("Sent SYN-ACK to client\n");
    }

    // Check for ACK packet
    if (tcp_header->ack == 1) {

      printf("Received ACK from client\n");
      break; // Handshake complete
    }
  }

  close(sockfd);
  return 0;
}

int server2() {
  int sockfd;
  struct sockaddr_in server_addr, client_addr;
  socklen_t addr_len;
  char buffer[BUFFER_SIZE];

  // Create socket

  printf("Server about to create socket\n\r");
  // NOTE: Need sudo privilages to create a raw socket I guess
  sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_TCP);
  if (sockfd < 0) {
    perror("Socket creation failed");

    exit(EXIT_FAILURE);
  }
  printf("Socket: AF_INET, SOCK_RAW, IPPROTO_TCP\n\r");

  // Set up server address
  memset(&server_addr, 0, sizeof(server_addr));
  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = INADDR_ANY;
  server_addr.sin_port = htons(PORT);

  // Bind the socket
  if (bind(sockfd, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
    perror("Bind failed");
    close(sockfd);
    exit(EXIT_FAILURE);
  }
  printf("Bind: family %d, s_addr %d, port %d \n\r", server_addr.sin_family,
         server_addr.sin_addr.s_addr, server_addr.sin_port);

  // Listen for incoming packets
  while (1) {
    addr_len = sizeof(client_addr);
    int bytes_received = recvfrom(sockfd, buffer, BUFFER_SIZE, 0,
                                  (struct sockaddr *)&client_addr, &addr_len);
    if (bytes_received < 0) {
      perror("Receive failed");
      continue;
    }

    // Process the received packet
    struct iphdr *ip_header = (struct iphdr *)buffer;
    struct tcphdr *tcp_header =
        (struct tcphdr *)(buffer + (ip_header->ihl * 4));
    int payload_size =
        bytes_received - (ip_header->ihl * 4) - (tcp_header->doff * 4);

    log_tcp_info(ip_header, tcp_header, payload_size);
  }

  close(sockfd);
  return 0;
}

#define SERVER_IP "127.0.0.1"

#define SERVER_PORT 8080
#define PAYLOAD_SIZE 65507 // Maximum TCP payload size

// tcp_client.c
#include <arpa/inet.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define SERVER_IP "127.0.0.1"
#define BUFFER_SIZE 65535

int client() {
  int sockfd;
  struct sockaddr_in server_addr;
  char packet[BUFFER_SIZE];

  // Create raw socket
  sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_TCP);

  if (sockfd < 0) {
    perror("Socket creation failed");
    exit(EXIT_FAILURE);
  }

  // Set up server address
  memset(&server_addr, 0, sizeof(server_addr));
  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = inet_addr(SERVER_IP);
  server_addr.sin_port = htons(PORT);

  // Prepare SYN packet
  memset(packet, 0, BUFFER_SIZE);
  struct iphdr *ip_hdr = (struct iphdr *)packet;

  struct tcphdr *tcp_hdr = (struct tcphdr *)(packet + sizeof(struct iphdr));

  // Fill IP header
  ip_hdr->version = 4;

  ip_hdr->ihl = 5;
  ip_hdr->tot_len = sizeof(struct iphdr) + sizeof(struct tcphdr);
  ip_hdr->protocol = IPPROTO_TCP;
  ip_hdr->saddr = inet_addr(SERVER_IP);        // Source IP
  ip_hdr->daddr = server_addr.sin_addr.s_addr; // Destination IP

  // Fill TCP header
  tcp_hdr->source = htons(PORT);
  tcp_hdr->dest = htons(PORT);
  tcp_hdr->seq = htonl(0); // Initial sequence number

  tcp_hdr->ack_seq = 0; // No acknowledgment yet
  tcp_hdr->doff = 5;    // TCP header size
  tcp_hdr->syn = 1;     // SYN flag
  tcp_hdr->check =
      checksum(tcp_hdr, sizeof(struct tcphdr)); // Calculate checksum

  // Send SYN packet
  sendto(sockfd, packet, sizeof(struct iphdr) + sizeof(struct tcphdr), 0,
         (struct sockaddr *)&server_addr, sizeof(server_addr));
  printf("Sent SYN to server\n");

  // Receive SYN-ACK packet
  char buffer[BUFFER_SIZE];
  socklen_t addr_len = sizeof(server_addr);
  recvfrom(sockfd, buffer, BUFFER_SIZE, 0, (struct sockaddr *)&server_addr,
           &addr_len);

  // Process SYN-ACK packet
  ip_hdr = (struct iphdr *)buffer;
  tcp_hdr = (struct tcphdr *)(buffer + (ip_hdr->ihl * 4));

  // Prepare ACK packet
  memset(packet, 0, BUFFER_SIZE);
  ip_hdr = (struct iphdr *)packet;
  tcp_hdr = (struct tcphdr *)(packet + sizeof(struct iphdr));

  // Fill IP header
  ip_hdr->version = 4;
  ip_hdr->ihl = 5;
  ip_hdr->tot_len = sizeof(struct iphdr) + sizeof(struct tcphdr);
  ip_hdr->protocol = IPPROTO_TCP;
  ip_hdr->saddr = inet_addr(SERVER_IP);        // Source IP
  ip_hdr->daddr = server_addr.sin_addr.s_addr; // Destination IP

  // Fill TCP header
  tcp_hdr->source = htons(PORT);

  tcp_hdr->dest = htons(PORT);
  tcp_hdr->seq = htonl(1); // Acknowledge the server's SYN
  tcp_hdr->ack_seq =
      ntohl(tcp_hdr->seq) + 1; // Acknowledge the server's sequence number
  tcp_hdr->doff = 5;           // TCP header size
  tcp_hdr->ack = 1;            // ACK flag
  tcp_hdr->check =
      checksum(tcp_hdr, sizeof(struct tcphdr)); // Calculate checksum

  // Send ACK packet
  sendto(sockfd, packet, sizeof(struct iphdr) + sizeof(struct tcphdr), 0,
         (struct sockaddr *)&server_addr, sizeof(server_addr));
  printf("Sent ACK to server\n");

  close(sockfd);
  return 0;
}
// tcp_client.c
#include <arpa/inet.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define SERVER_IP "127.0.0.1"
#define BUFFER_SIZE 65535

int client_raw_looping() {
  int sockfd;
  struct sockaddr_in server_addr;
  char packet[BUFFER_SIZE];
  char input[BUFFER_SIZE];

  // Create raw socket
  sockfd = socket(AF_INET, SOCK_RAW, IPPROTO_TCP);
  if (sockfd < 0) {
    perror("Socket creation failed");
    exit(EXIT_FAILURE);
  }

  // Set up server address
  memset(&server_addr, 0, sizeof(server_addr));
  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = inet_addr(SERVER_IP);
  server_addr.sin_port = htons(PORT);

  // Prepare SYN packet
  memset(packet, 0, BUFFER_SIZE);

  struct iphdr *ip_hdr = (struct iphdr *)packet;
  struct tcphdr *tcp_hdr = (struct tcphdr *)(packet + sizeof(struct iphdr));

  // Fill IP header
  ip_hdr->version = 4;
  ip_hdr->ihl = 5;
  ip_hdr->tot_len = sizeof(struct iphdr) + sizeof(struct tcphdr);
  ip_hdr->protocol = IPPROTO_TCP;
  ip_hdr->saddr = inet_addr(SERVER_IP);        // Source IP
  ip_hdr->daddr = server_addr.sin_addr.s_addr; // Destination IP

  // Fill TCP header

  tcp_hdr->source = htons(PORT);
  tcp_hdr->dest = htons(PORT);

  tcp_hdr->seq = htonl(0); // Initial sequence number
  tcp_hdr->ack_seq = 0;    // No acknowledgment yet
  tcp_hdr->doff = 5;       // TCP header size
  tcp_hdr->syn = 1;        // SYN flag
  tcp_hdr->check =
      checksum(tcp_hdr, sizeof(struct tcphdr)); // Calculate checksum

  // Send SYN packet
  sendto(sockfd, packet, sizeof(struct iphdr) + sizeof(struct tcphdr), 0,
         (struct sockaddr *)&server_addr, sizeof(server_addr));
  printf("Sent SYN to server\n");

  // Receive SYN-ACK packet

  char buffer[BUFFER_SIZE];
  socklen_t addr_len = sizeof(server_addr);
  recvfrom(sockfd, buffer, BUFFER_SIZE, 0, (struct sockaddr *)&server_addr,
           &addr_len);

  // Process SYN-ACK packet
  ip_hdr = (struct iphdr *)buffer;
  tcp_hdr = (struct tcphdr *)(buffer + (ip_hdr->ihl * 4));

  // Prepare ACK packet
  memset(packet, 0, BUFFER_SIZE);
  ip_hdr = (struct iphdr *)packet;
  tcp_hdr = (struct tcphdr *)(packet + sizeof(struct iphdr));

  // Fill IP header
  ip_hdr->version = 4;
  ip_hdr->ihl = 5;
  ip_hdr->tot_len = sizeof(struct iphdr) + sizeof(struct tcphdr);
  ip_hdr->protocol = IPPROTO_TCP;
  ip_hdr->saddr = inet_addr(SERVER_IP);        // Source IP
  ip_hdr->daddr = server_addr.sin_addr.s_addr; // Destination IP

  // Fill TCP header
  tcp_hdr->source = htons(PORT);
  tcp_hdr->dest = htons(PORT);
  tcp_hdr->seq = htonl(1); // Acknowledge the server's SYN
  tcp_hdr->ack_seq =
      ntohl(tcp_hdr->seq) + 1; // Acknowledge the server's sequence number
  tcp_hdr->doff = 5;           // TCP header size
  tcp_hdr->ack = 1;            // ACK flag
  tcp_hdr->check =
      checksum(tcp_hdr, sizeof(struct tcphdr)); // Calculate checksum

  // Send ACK packet
  sendto(sockfd, packet, sizeof(struct iphdr) + sizeof(struct tcphdr), 0,
         (struct sockaddr *)&server_addr, sizeof(server_addr));
  printf("Sent ACK to server\n");

  // Loop to send user input
  while (1) {
    printf("Enter message to send (type 'quit' or 'exit' to stop): ");
    fgets(input, BUFFER_SIZE, stdin);
    input[strcspn(input, "\n")] = 0; // Remove newline character

    if (strcmp(input, "quit") == 0 || strcmp(input, "exit") == 0) {
      break; // Exit the loop
    }

    // Prepare data packet
    memset(packet, 0, BUFFER_SIZE);
    ip_hdr = (struct iphdr *)packet;
    tcp_hdr = (struct tcphdr *)(packet + sizeof(struct iphdr));

    // Fill IP header
    ip_hdr->version = 4;
    ip_hdr->ihl = 5;
    ip_hdr->tot_len =
        sizeof(struct iphdr) + sizeof(struct tcphdr) + strlen(input);
    ip_hdr->protocol = IPPROTO_TCP;
    ip_hdr->saddr = inet_addr(SERVER_IP);        // Source IP
    ip_hdr->daddr = server_addr.sin_addr.s_addr; // Destination IP

    // Fill TCP header
    tcp_hdr->source = htons(PORT);
    tcp_hdr->dest = htons(PORT);
    tcp_hdr->seq = htonl(2); // Increment sequence number
    tcp_hdr->ack_seq =
        ntohl(tcp_hdr->seq) + 1; // Acknowledge the server's sequence number
    tcp_hdr->doff = 5;           // TCP header size

    tcp_hdr->ack = 1; // ACK flag
    tcp_hdr->check =
        checksum(tcp_hdr, sizeof(struct tcphdr)); // Calculate checksum

    // Copy user input to packet
    memcpy(packet + sizeof(struct iphdr) + sizeof(struct tcphdr), input,
           strlen(input));

    // Send data packet
    sendto(sockfd, packet,
           sizeof(struct iphdr) + sizeof(struct tcphdr) + strlen(input), 0,
           (struct sockaddr *)&server_addr, sizeof(server_addr));
    printf("Sent message: %s\n", input);
  }

  close(sockfd);
  return 0;
}
// tcp_client.c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <unistd.h>
#include <arpa/inet.h>

// #define SERVER_IP "127.0.0.1"
#define SERVER_IP "localhost"
#define CLIENT_BUFFER_SIZE 1024



int client_stream() {
    int sockfd;
    struct sockaddr_in server_addr;
    char input[CLIENT_BUFFER_SIZE];

    // Create socket
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        perror("Socket creation failed");
        exit(EXIT_FAILURE);
    }

    // Set up server address
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = inet_addr(SERVER_IP);
    server_addr.sin_port = htons(PORT);


    printf(
      "Trying to connect to server port:%d server_ip:%s\n\r",
           PORT, SERVER_IP);
    // Connect to the server
    if (connect(sockfd, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0){
        perror("Connection to server failed");
        close(sockfd);

        exit(EXIT_FAILURE);
    }


    printf("Connected to server\n");

    // Loop to send user input
    while (1) {
        printf("Enter message to send (type 'quit' or 'exit' to stop): ");
        fgets(input, CLIENT_BUFFER_SIZE, stdin);
        input[strcspn(input, "\n")] = 0; // Remove newline character

        if (strcmp(input, "quit") == 0 || strcmp(input, "exit") == 0) {
            break; // Exit the loop
        }

        // Send the input to the server
        send(sockfd, input, strlen(input), 0);
        printf("Sent message: %s\n", input);
    }


    // Close the socket
    close(sockfd);
    return 0;
}

