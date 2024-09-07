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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>

#define PORT 8080
#define BUFFER_SIZE 1024

void print_tcp_header(struct tcphdr *tcph) {
    printf("TCP Header:\n");
    printf("Source Port: %d\n", ntohs(tcph->source));
    printf("Destination Port: %d\n", ntohs(tcph->dest));
    printf("Sequence Number: %u\n", ntohl(tcph->seq));
    printf("Acknowledgment Number: %u\n", ntohl(tcph->ack_seq));
    printf("Header Length: %d\n", tcph->doff * 4);
    printf("Flags: ");
    if (tcph->fin) printf("FIN ");
    if (tcph->syn) printf("SYN ");
    if (tcph->rst) printf("RST ");
    if (tcph->psh) printf("PSH ");
    if (tcph->ack) printf("ACK ");
    if (tcph->urg) printf("URG ");
    printf("\n");
    printf("Window Size: %d\n", ntohs(tcph->window));
    printf("Checksum: 0x%04x\n", ntohs(tcph->check));
    printf("Urgent Pointer: %d\n\n", ntohs(tcph->urg_ptr));
}

void server() {
    int server_fd, new_socket;
    struct sockaddr_in address;
    int addrlen = sizeof(address);
    char buffer[BUFFER_SIZE] = {0};

    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        perror("socket failed");
        exit(EXIT_FAILURE);
    }

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(PORT);

    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        perror("bind failed");
        exit(EXIT_FAILURE);
    }

    if (listen(server_fd, 3) < 0) {
        perror("listen failed");
        exit(EXIT_FAILURE);
    }

    printf("Server listening on port %d\n", PORT);


    if ((new_socket = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen)) < 0) {
        perror("accept failed");
        exit(EXIT_FAILURE);
    }

    while (1) {
        int valread = read(new_socket, buffer, BUFFER_SIZE);
        if (valread <= 0) break;

        struct tcphdr *tcph = (struct tcphdr *)buffer;
        print_tcp_header(tcph);


        printf("Received: %s\n", buffer + sizeof(struct tcphdr));
        send(new_socket, buffer, valread, 0);
    }

    close(new_socket);
    close(server_fd);
}

void client() {
    int sock = 0;
    struct sockaddr_in serv_addr;
    char buffer[BUFFER_SIZE] = {0};

    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("Socket creation error");
        exit(EXIT_FAILURE);
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);

    if (inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) <= 0) {
        perror("Invalid address/ Address not supported");
        exit(EXIT_FAILURE);
    }

    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Connection Failed");
        exit(EXIT_FAILURE);
    }

    printf("Connected to server. Type messages to send (Ctrl+D to exit):\n");

    while (fgets(buffer + sizeof(struct tcphdr), BUFFER_SIZE - sizeof(struct tcphdr), stdin) != NULL) {
        struct tcphdr *tcph = (struct tcphdr *)buffer;

        memset(tcph, 0, sizeof(struct tcphdr));
        tcph->source = htons(12345);  // Example source port
        tcph->dest = htons(PORT);
        tcph->seq = htonl(1);  // Example sequence number
        tcph->ack_seq = 0;
        tcph->doff = 5;  // Data offset: 5 x 32-bit words
        tcph->fin = 0;
        tcph->syn = 0;
        tcph->rst = 0;
        tcph->psh = 1;
        tcph->ack = 0;
        tcph->urg = 0;
        tcph->window = htons(5840);  // Example window size
        tcph->check = 0;  // Should be calculated properly in a real implementation
        tcph->urg_ptr = 0;

        send(sock, buffer, strlen(buffer + sizeof(struct tcphdr)) + sizeof(struct tcphdr), 0);
        print_tcp_header(tcph);


        int valread = read(sock, buffer, BUFFER_SIZE);
        tcph = (struct tcphdr *)buffer;
        print_tcp_header(tcph);
        printf("Server response: %s\n", buffer + sizeof(struct tcphdr));
    }

    close(sock);
}

int main(int argc, char *argv[]) {

    if (argc != 2) {
        fprintf(stderr, "Usage: %s [server|client]\n", argv[0]);
        exit(EXIT_FAILURE);
    }

    if (strcmp(argv[1], "server") == 0) {
        server();
    } else if (strcmp(argv[1], "client") == 0) {
        client();
    } else {
        fprintf(stderr, "Invalid argument. Use 'server' or 'client'.\n");
        exit(EXIT_FAILURE);
    }

    return 0;
}
