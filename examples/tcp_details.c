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

#define PORT 6969

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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <time.h>

#define BUFFER_SIZE 1024

#define INITIAL_CWND 10
#define INITIAL_SSTHRESH 65535

struct tcp_state {
    uint32_t seq_num;           // Sequence number
    uint32_t ack_num;           // Acknowledgment number
    uint16_t recv_window;       // Receive window
    uint32_t cwnd;              // Congestion window
    uint32_t ssthresh;          // Slow start threshold
    uint8_t dup_acks;           // Duplicate ACKs counter
    time_t rtt;                 // Round Trip Time
    time_t srtt;                // Smoothed Round Trip Time
    time_t rto;                 // Retransmission Timeout
    enum {
        CLOSED,
        LISTEN,
        SYN_SENT,
        SYN_RECEIVED,
        ESTABLISHED,
        FIN_WAIT_1,
        FIN_WAIT_2,
        CLOSE_WAIT,
        CLOSING,
        LAST_ACK,
        TIME_WAIT
    } connection_state;
};



void print_tcp_state(struct tcp_state *state) {
    printf("TCP State:\n");
    printf("Sequence Number: %u\n", state->seq_num);
    printf("Acknowledgment Number: %u\n", state->ack_num);
    printf("Receive Window: %u\n", state->recv_window);
    printf("Congestion Window: %u\n", state->cwnd);
    printf("Slow Start Threshold: %u\n", state->ssthresh);
    printf("Duplicate ACKs: %u\n", state->dup_acks);
    printf("RTT: %ld ms\n", state->rtt);
    printf("Smoothed RTT: %ld ms\n", state->srtt);
    printf("Retransmission Timeout: %ld ms\n", state->rto);
    printf("Connection State: ");
    switch(state->connection_state) {
        case CLOSED: printf("CLOSED\n"); break;
        case LISTEN: printf("LISTEN\n"); break;
        case SYN_SENT: printf("SYN_SENT\n"); break;
        case SYN_RECEIVED: printf("SYN_RECEIVED\n"); break;
        case ESTABLISHED: printf("ESTABLISHED\n"); break;
        case FIN_WAIT_1: printf("FIN_WAIT_1\n"); break;

        case FIN_WAIT_2: printf("FIN_WAIT_2\n"); break;
        case CLOSE_WAIT: printf("CLOSE_WAIT\n"); break;
        case CLOSING: printf("CLOSING\n"); break;
        case LAST_ACK: printf("LAST_ACK\n"); break;
        case TIME_WAIT: printf("TIME_WAIT\n"); break;
    }
    printf("\n");
}

void init_tcp_state(struct tcp_state *state) {
    state->seq_num = rand();
    state->ack_num = 0;
    state->recv_window = 65535;
    state->cwnd = INITIAL_CWND;

    state->ssthresh = INITIAL_SSTHRESH;
    state->dup_acks = 0;

    state->rtt = 0;
    state->srtt = 0;
    state->rto = 3000;  // Initial RTO of 3 seconds
    state->connection_state = CLOSED;
}

void update_tcp_state(struct tcp_state *state, struct tcphdr *tcph) {
    // Update sequence and acknowledgment numbers
    if (tcph->syn) state->ack_num = ntohl(tcph->seq) + 1;
    else if (tcph->fin) state->ack_num = ntohl(tcph->seq) + 1;
    else state->ack_num = ntohl(tcph->seq) + 1; // Simplified, assumes 1 byte of data

    state->seq_num = ntohl(tcph->ack_seq);
    state->recv_window = ntohs(tcph->window);

    // Simplified state transitions
    if (tcph->syn && !tcph->ack) {
        state->connection_state = SYN_RECEIVED;
    } else if (tcph->syn && tcph->ack) {
        state->connection_state = ESTABLISHED;

    } else if (tcph->fin) {
        state->connection_state = CLOSE_WAIT;
    }


    // Simplified congestion control
    if (tcph->ack) {

        if (state->cwnd < state->ssthresh) {
            // Slow start
            state->cwnd += 1;
        } else {
            // Congestion avoidance
            state->cwnd += 1 / state->cwnd;
        }
    }

    // Simplified RTT calculation (not accurate, just for demonstration)
    state->rtt = rand() % 100 + 50;  // Random RTT between 50-150ms
    state->srtt = (state->srtt * 7 + state->rtt) / 8;
    state->rto = state->srtt * 2;
}

void server() {
    int server_fd, new_socket;
    struct sockaddr_in address;
    int addrlen = sizeof(address);
    char buffer[BUFFER_SIZE] = {0};
    struct tcp_state state;

    init_tcp_state(&state);
    state.connection_state = LISTEN;

    // ... (socket creation and binding code remains the same)

    printf("Server listening on port %d\n", PORT);
    print_tcp_state(&state);


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

        update_tcp_state(&state, tcph);
        print_tcp_state(&state);

        printf("Received: %s\n", buffer + sizeof(struct tcphdr));

        send(new_socket, buffer, valread, 0);
    }

    close(new_socket);
    close(server_fd);
}

int client() {
    int sock = 0;
    struct sockaddr_in serv_addr;
    char buffer[BUFFER_SIZE] = {0};

    struct tcp_state state;

    init_tcp_state(&state);


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
    print_tcp_state(&state);

    while (fgets(buffer + sizeof(struct tcphdr), BUFFER_SIZE - sizeof(struct tcphdr), stdin) != NULL) {
        struct tcphdr *tcph = (struct tcphdr *)buffer;
        memset(tcph, 0, sizeof(struct tcphdr));
        tcph->source = htons(12345);
        tcph->dest = htons(PORT);
        tcph->seq = htonl(state.seq_num);
        tcph->ack_seq = htonl(state.ack_num);
        tcph->doff = 5;
        tcph->psh = 1;
        tcph->ack = 1;
        tcph->window = htons(state.recv_window);


        send(sock, buffer, strlen(buffer + sizeof(struct tcphdr)) + sizeof(struct tcphdr), 0);
        print_tcp_header(tcph);
        update_tcp_state(&state, tcph);

        print_tcp_state(&state);


        int valread = read(sock, buffer, BUFFER_SIZE);
        tcph = (struct tcphdr *)buffer;
        print_tcp_header(tcph);
        update_tcp_state(&state, tcph);
        print_tcp_state(&state);
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
