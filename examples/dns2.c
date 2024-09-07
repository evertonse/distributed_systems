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
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <unistd.h>


#define MAX_ITERATIONS 1000

void print_addrinfo(struct addrinfo *res, int iteration) {
    char ipstr[INET6_ADDRSTRLEN];
    void *addr;
    char *ipver;


    if (res->ai_family == AF_INET) {
        struct sockaddr_in *ipv4 = (struct sockaddr_in *)res->ai_addr;
        addr = &(ipv4->sin_addr);
        ipver = "IPv4";
    } else {
        struct sockaddr_in6 *ipv6 = (struct sockaddr_in6 *)res->ai_addr;
        addr = &(ipv6->sin6_addr);
        ipver = "IPv6";
    }

    inet_ntop(res->ai_family, addr, ipstr, sizeof ipstr);
    printf("Iteration %d: %s: %s\n", iteration, ipver, ipstr);

    printf("  ai_flags: 0x%x\n", res->ai_flags);
    printf("  ai_family: %d\n", res->ai_family);
    printf("  ai_socktype: %d\n", res->ai_socktype);
    printf("  ai_protocol: %d\n", res->ai_protocol);
    printf("  ai_addrlen: %d\n", res->ai_addrlen);

    if (res->ai_canonname) {
        printf("  ai_canonname: %s\n", res->ai_canonname);
    }
}

int main(int argc, char *argv[]) {
    if (argc != 2) {
        fprintf(stderr, "Usage: %s <hostname>\n", argv[0]);
        exit(1);
    }

    char *hostname = argv[1];
    struct addrinfo hints, *res, *p;
    int status;

    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;

    for (int i = 0; i < MAX_ITERATIONS; i++) {
        printf("\n--- Iteration %d ---\n", i + 1);
        status = getaddrinfo(hostname, NULL, &hints, &res);
        if (status != 0) {
            fprintf(stderr, "getaddrinfo error: %s\n", gai_strerror(status));
            exit(1);
        }

        for (p = res; p != NULL; p = p->ai_next) {
            print_addrinfo(p, i + 1);
        }


        freeaddrinfo(res);

        // Sleep for a short time to allow for potential DNS changes
        sleep(1);

    }

    return 0;
}
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>

#include <sys/socket.h>
#include <unistd.h>

#define DNS_SERVER "8.8.8.8"  // Google's public DNS server
#define DNS_PORT 53
#define BUFFER_SIZE 512


// DNS header structure
struct DNSHeader {
    unsigned short id;       // Identification number
    unsigned short flags;    // DNS flags
    unsigned short qdcount;  // Number of questions
    unsigned short ancount;  // Number of answers
    unsigned short nscount;  // Number of authority records
    unsigned short arcount;  // Number of additional records
};

// DNS question structure
struct DNSQuestion {
    unsigned short qtype;  // Query type
    unsigned short qclass; // Query class
};

// Function prototypes
void create_dns_query(unsigned char *buf, const char *hostname, int *query_size);
void send_dns_query(int sock, struct sockaddr_in *server, unsigned char *query, int query_size);
void parse_dns_response(unsigned char *response);

int main2(int argc, char *argv[]) {
    if (argc < 2) {
        printf("Usage: %s <hostname>\n", argv[0]);
        return 1;
    }

    const char *hostname = argv[1];
    unsigned char query[BUFFER_SIZE];
    unsigned char response[BUFFER_SIZE];
    int query_size;

    // Create DNS query
    create_dns_query(query, hostname, &query_size);


    // Create a UDP socket
    int sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (sock < 0) {
        perror("Socket creation failed");
        return 1;
    }

    // DNS server address
    struct sockaddr_in server;
    server.sin_family = AF_INET;
    server.sin_port = htons(DNS_PORT);
    server.sin_addr.s_addr = inet_addr(DNS_SERVER);

    // Send the DNS query
    send_dns_query(sock, &server, query, query_size);

    // Receive the DNS response
    socklen_t server_len = sizeof(server);
    int response_size = recvfrom(sock, response, sizeof(response), 0, (struct sockaddr *)&server, &server_len);
    if (response_size < 0) {
        perror("Receiving DNS response failed");
        close(sock);
        return 1;
    }

    // Parse and print the DNS response
    parse_dns_response(response);

    // Close the socket
    close(sock);
    return 0;
}

void create_dns_query(unsigned char *buf, const char *hostname, int *query_size) {
    struct DNSHeader *dns_header = (struct DNSHeader *)buf;
    dns_header->id = htons(0x1234);          // Random ID
    dns_header->flags = htons(0x0100);       // Standard query, recursion desired

    dns_header->qdcount = htons(1);          // One question
    dns_header->ancount = 0;
    dns_header->nscount = 0;

    dns_header->arcount = 0;

    // Pointer to the query section
    unsigned char *qname = buf + sizeof(struct DNSHeader);
    const char *label_start = hostname;
    const char *label_end;
    while ((label_end = strchr(label_start, '.')) != NULL) {
        *qname++ = label_end - label_start;
        memcpy(qname, label_start, label_end - label_start);
        qname += label_end - label_start;
        label_start = label_end + 1;
    }
    *qname++ = strlen(label_start);
    strcpy((char *)qname, label_start);
    qname += strlen(label_start) + 1;

    // DNS question
    struct DNSQuestion *question = (struct DNSQuestion *)qname;

    question->qtype = htons(1);  // Type A (IPv4 address)
    question->qclass = htons(1); // Class IN (internet)

    // Total query size
    *query_size = qname + sizeof(struct DNSQuestion) - buf;
}

void send_dns_query(int sock, struct sockaddr_in *server, unsigned char *query, int query_size) {
    if (sendto(sock, query, query_size, 0, (struct sockaddr *)server, sizeof(*server)) < 0) {
        perror("Sending DNS query failed");
        exit(1);
    }
}


void parse_dns_response(unsigned char *response) {
    struct DNSHeader *dns_header = (struct DNSHeader *)response;

    // Check the number of answers
    int answer_count = ntohs(dns_header->ancount);
    if (answer_count == 0) {
        printf("No answers found\n");

        return;
    }

    // Skip over the header and question sections
    unsigned char *answer = response + sizeof(struct DNSHeader);
    while (*answer != 0) { answer++; }  // Skip over the query name
    answer += 5;  // Skip over the null byte and question type/class

    // Process the answers

    for (int i = 0; i < answer_count; i++) {
        answer += 2;  // Skip the answer name pointer
        unsigned short type = ntohs(*(unsigned short *)answer);
        answer += 8;  // Skip type, class, TTL
        unsigned short data_length = ntohs(*(unsigned short *)answer);
        answer += 2;  // Skip data length

        // If it's an A record (IPv4 address)
        if (type == 1 && data_length == 4) {
            struct in_addr addr;
            memcpy(&addr, answer, 4);
            printf("IP Address: %s\n", inet_ntoa(addr));
            return;
        }
        answer += data_length;  // Skip the rest of the answer
    }

    printf("No valid A record found\n");
}

