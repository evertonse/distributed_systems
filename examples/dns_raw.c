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
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <netinet/in.h>


#define MAX_ITERATIONS 1000
#define MAX_DNS_SIZE 512
#define DNS_PORT 53

// DNS header structure
struct DNS_HEADER {
    unsigned short id;
    unsigned char rd :1;
    unsigned char tc :1;
    unsigned char aa :1;
    unsigned char opcode :4;
    unsigned char qr :1;
    unsigned char rcode :4;
    unsigned char cd :1;
    unsigned char ad :1;
    unsigned char z :1;

    unsigned char ra :1;
    unsigned short q_count;
    unsigned short ans_count;
    unsigned short auth_count;
    unsigned short add_count;
};


// Query structure
struct QUESTION {
    unsigned short qtype;
    unsigned short qclass;
};

// Resource record structure
struct R_DATA {
    unsigned short type;
    unsigned short _class;
    unsigned int ttl;
    unsigned short data_len;
};

// Function to convert hostname to DNS format
void ChangetoDnsNameFormat(unsigned char* dns, unsigned char* host) {
    int lock = 0, i;
    strcat((char*)host, ".");
    for (i = 0; i < strlen((char*)host); i++) {
        if (host[i] == '.') {
            *dns++ = i - lock;
            for (; lock < i; lock++) {
                *dns++ = host[lock];
            }
            lock++;
        }
    }
    *dns++ = '\0';
}

// Function to create a DNS query packet
void CreateDNSQuery(unsigned char *host, int query_type, unsigned char *buf, int *query_len) {
    struct DNS_HEADER *dns = NULL;
    unsigned char *qname = NULL;

    struct QUESTION *qinfo = NULL;

    dns = (struct DNS_HEADER *)buf;

    dns->id = (unsigned short)htons(getpid());

    dns->qr = 0;
    dns->opcode = 0;
    dns->aa = 0;
    dns->tc = 0;
    dns->rd = 1;
    dns->ra = 0;
    dns->z = 0;
    dns->ad = 0;
    dns->cd = 0;
    dns->rcode = 0;
    dns->q_count = htons(1);
    dns->ans_count = 0;
    dns->auth_count = 0;
    dns->add_count = 0;

    qname = (unsigned char*)&buf[sizeof(struct DNS_HEADER)];
    ChangetoDnsNameFormat(qname, (unsigned char*)host);

    qinfo = (struct QUESTION*)&buf[sizeof(struct DNS_HEADER) + (strlen((const char*)qname) + 1)];
    qinfo->qtype = htons(query_type);
    qinfo->qclass = htons(1);

    *query_len = sizeof(struct DNS_HEADER) + (strlen((const char*)qname) + 1) + sizeof(struct QUESTION);
}

// Function to print the IPv4 address from the response and open browser
void PrintIPv4AddressAndOpenBrowser(unsigned char* reader, int data_len, int is_authoritative, const char* hostname) {
    if (data_len == 4) {  // IPv4 address
        unsigned char ip[4];
        memcpy(ip, reader, data_len);
        printf("IPv4 Address: %d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3]);
        if (is_authoritative) {
            printf(" (Authoritative Answer)\n");
            printf("Opening browser for %s\n", hostname);
            
            char command[256];
            #ifdef __APPLE__
                sprintf(command, "open http://%s", hostname);
            #elif __linux__
                sprintf(command, "xdg-open http://%s", hostname);
            #elif _WIN32
                sprintf(command, "start http://%s", hostname);
            #else
                printf("Unsupported operating system for automatic browser opening.\n");
                return;
            #endif
            
            system(command);
        }
        printf("\n");
    }
}

int main(int argc, char *argv[]) {
    if (argc != 2) {
        fprintf(stderr, "Usage: %s <hostname>\n", argv[0]);
        exit(1);
    }

    char *hostname = argv[1];
    unsigned char buf[MAX_DNS_SIZE], *qname, *reader;
    int i, j, query_len, s;
    struct sockaddr_in dest;

    s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);


    dest.sin_family = AF_INET;
    dest.sin_port = htons(DNS_PORT);
    dest.sin_addr.s_addr = inet_addr("8.8.8.8");  // Google's public DNS

    int authoritative_found = 0;


    for (int iteration = 0; iteration < MAX_ITERATIONS && !authoritative_found; iteration++) {
        printf("\n--- Iteration %d ---\n", iteration + 1);


        CreateDNSQuery((unsigned char*)hostname, 1, buf, &query_len);  // 1 for A record

        if (sendto(s, (char*)buf, query_len, 0, (struct sockaddr*)&dest, sizeof(dest)) < 0) {
            perror("sendto failed");

            continue;

        }

        int recv_len = recvfrom(s, (char*)buf, MAX_DNS_SIZE, 0, NULL, NULL);
        if (recv_len < 0) {
            perror("recvfrom failed");
            continue;
        }

        struct DNS_HEADER *dns = (struct DNS_HEADER*)buf;
        reader = &buf[sizeof(struct DNS_HEADER)];
        qname = reader;

        reader += strlen((const char*)qname) + 1 + sizeof(struct QUESTION);

        printf("Response Code: %d\n", dns->rcode);
        printf("Questions: %d\n", ntohs(dns->q_count));
        printf("Answers: %d\n", ntohs(dns->ans_count));
        printf("Authoritative Answer: %s\n", dns->aa ? "Yes" : "No");

        for (i = 0; i < ntohs(dns->ans_count); i++) {
            reader += 2;  // Skip name field

            struct R_DATA *resource = (struct R_DATA*)(reader);
            reader += sizeof(struct R_DATA);

            if (ntohs(resource->type) == 1) {  // If its an A record
                PrintIPv4AddressAndOpenBrowser(reader, ntohs(resource->data_len), dns->aa, hostname);

                if (dns->aa) {
                    authoritative_found = 1;
                    printf("Found authoritative IPv4 address for %s\n", hostname);
                }
            }

            reader += ntohs(resource->data_len);

        }

        if (authoritative_found) {
            break;
        }

        sleep(1);  // Wait before next iteration
    }

    if (!authoritative_found) {
        printf("Could not find an authoritative answer after %d iterations.\n", MAX_ITERATIONS);

    }

    close(s);
    return 0;
}
// #include <stdio.h>
// #include <stdlib.h>
// #include <string.h>
// #include <unistd.h>
// #include <arpa/inet.h>
// #include <sys/socket.h>
// #include <netinet/in.h>
//
//
// #define MAX_ITERATIONS 10
// #define MAX_DNS_SIZE 512
// #define DNS_PORT 53
//
// // DNS header structure
// struct DNS_HEADER {
//     unsigned short id;
//     unsigned char rd :1;
//     unsigned char tc :1;
//     unsigned char aa :1;
//     unsigned char opcode :4;
//     unsigned char qr :1;
//     unsigned char rcode :4;
//     unsigned char cd :1;
//     unsigned char ad :1;
//     unsigned char z :1;
//
//     unsigned char ra :1;
//     unsigned short q_count;
//     unsigned short ans_count;
//     unsigned short auth_count;
//     unsigned short add_count;
// };
//
// // Query structure
// struct QUESTION {
//     unsigned short qtype;
//     unsigned short qclass;
// };
//
// // Resource record structure
// struct R_DATA {
//     unsigned short type;
//     unsigned short _class;
//     unsigned int ttl;
//     unsigned short data_len;
// };
//
// // Pointer to resource record structure
// #pragma pack(push, 1)
// struct RES_RECORD {
//     unsigned char *name;
//     struct R_DATA *resource;
//     unsigned char *rdata;
// };
// #pragma pack(pop)
//
// // Function to convert hostname to DNS format
// void ChangetoDnsNameFormat(unsigned char* dns, unsigned char* host) {
//     int lock = 0, i;
//     strcat((char*)host, ".");
//     for (i = 0; i < strlen((char*)host); i++) {
//         if (host[i] == '.') {
//             *dns++ = i - lock;
//             for (; lock < i; lock++) {
//                 *dns++ = host[lock];
//             }
//             lock++;
//         }
//     }
//     *dns++ = '\0';
// }
//
// // Function to create a DNS query packet
// void CreateDNSQuery(unsigned char *host, int query_type, unsigned char *buf, int *query_len) {
//     struct DNS_HEADER *dns = NULL;
//     unsigned char *qname = NULL;
//     struct QUESTION *qinfo = NULL;
//
//     dns = (struct DNS_HEADER *)buf;
//     dns->id = (unsigned short)htons(getpid());
//     dns->qr = 0;
//     dns->opcode = 0;
//     dns->aa = 0;
//
//     dns->tc = 0;
//     dns->rd = 1;
//
//     dns->ra = 0;
//     dns->z = 0;
//     dns->ad = 0;
//     dns->cd = 0;
//     dns->rcode = 0;
//     dns->q_count = htons(1);
//     dns->ans_count = 0;
//     dns->auth_count = 0;
//     dns->add_count = 0;
//
//     qname = (unsigned char*)&buf[sizeof(struct DNS_HEADER)];
//     ChangetoDnsNameFormat(qname, (unsigned char*)host);
//
//     qinfo = (struct QUESTION*)&buf[sizeof(struct DNS_HEADER) + (strlen((const char*)qname) + 1)];
//     qinfo->qtype = htons(query_type);
//     qinfo->qclass = htons(1);
//
//     *query_len = sizeof(struct DNS_HEADER) + (strlen((const char*)qname) + 1) + sizeof(struct QUESTION);
// }
//
// // Function to print the IPv4 address from the response
// void PrintIPv4Address(unsigned char* reader, int data_len) {
//     if (data_len == 4) {  // IPv4 address
//         unsigned char ip[4];
//         memcpy(ip, reader, data_len);
//         printf("IPv4 Address: %d.%d.%d.%d\n", ip[0], ip[1], ip[2], ip[3]);
//
//     }
// }
//
// int main(int argc, char *argv[]) {
//     if (argc != 2) {
//         fprintf(stderr, "Usage: %s <hostname>\n", argv[0]);
//         exit(1);
//     }
//
//     char *hostname = argv[1];
//     unsigned char buf[MAX_DNS_SIZE], *qname, *reader;
//     int i, j, query_len, s;
//     struct sockaddr_in dest;
//
//     s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
//
//     dest.sin_family = AF_INET;
//     dest.sin_port = htons(DNS_PORT);
//     dest.sin_addr.s_addr = inet_addr("8.8.8.8");  // Google's public DNS
//
//     for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
//         printf("\n--- Iteration %d ---\n", iteration + 1);
//
//         CreateDNSQuery((unsigned char*)hostname, 1, buf, &query_len);  // 1 for A record
//
//         if (sendto(s, (char*)buf, query_len, 0, (struct sockaddr*)&dest, sizeof(dest)) < 0) {
//             perror("sendto failed");
//             continue;
//         }
//
//         int recv_len = recvfrom(s, (char*)buf, MAX_DNS_SIZE, 0, NULL, NULL);
//         if (recv_len < 0) {
//             perror("recvfrom failed");
//             continue;
//         }
//
//         struct DNS_HEADER *dns = (struct DNS_HEADER*)buf;
//         reader = &buf[sizeof(struct DNS_HEADER)];
//         qname = reader;
//
//
//         reader += strlen((const char*)qname) + 1 + sizeof(struct QUESTION);
//
//         printf("Response Code: %d\n", dns->rcode);
//         printf("Questions: %d\n", ntohs(dns->q_count));
//         printf("Answers: %d\n", ntohs(dns->ans_count));
//
//         for (i = 0; i < ntohs(dns->ans_count); i++) {
//
//             reader += 2;  // Skip name field
//
//             struct R_DATA *resource = (struct R_DATA*)(reader);
//             reader += sizeof(struct R_DATA);
//
//             if (ntohs(resource->type) == 1) {  // If its an A record
//                 PrintIPv4Address(reader, ntohs(resource->data_len));
//             }
//             reader += ntohs(resource->data_len);
//
//         }
//
//         sleep(1);  // Wait before next iteration
//     }
//
//     close(s);
//     return 0;
// }
