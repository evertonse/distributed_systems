#if LOL
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
#include <sys/socket.h>
#include <netinet/in.h>

#include <arpa/inet.h>
#include <errno.h>

#define MAX_DNS_SIZE 512

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

struct QUESTION {
    unsigned short qtype;

    unsigned short qclass;
};

void dns_format(unsigned char* dns, const char* host) {
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


void print_bytes(unsigned char* buff, int length) {
    for (int i = 0; i < length; i++) {
        printf("%02x ", buff[i]);
        if ((i + 1) % 16 == 0) printf("\n");
    }
    printf("\n");
}

int main() {
    const char *host = "example.com";
    const char *dns_server = "198.41.0.4";  // a.root-servers.net
    unsigned char buf[MAX_DNS_SIZE] = {0};
    struct sockaddr_in dest;

    printf("Creating socket...\n");
    int sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (sock < 0) {
        perror("Socket creation failed");
        return 1;
    }

    printf("Setting up destination address...\n");
    memset(&dest, 0, sizeof(dest));
    dest.sin_family = AF_INET;
    dest.sin_port = htons(53);
    if (inet_pton(AF_INET, dns_server, &dest.sin_addr) <= 0) {
        perror("Invalid address");
        close(sock);
        return 1;
    }


    printf("Preparing DNS query...\n");
    struct DNS_HEADER *dns = (struct DNS_HEADER *)&buf;
    dns->id = (unsigned short) htons(getpid());
    dns->rd = 1;
    dns->tc = 0;
    dns->aa = 0;
    dns->opcode = 0;
    dns->qr = 0;
    dns->rcode = 0;
    dns->cd = 0;
    dns->ad = 0;
    dns->z = 0;

    dns->ra = 0;
    dns->q_count = htons(1);
    dns->ans_count = 0;
    dns->auth_count = 0;

    dns->add_count = 0;

    unsigned char *qname = (unsigned char*)&buf[sizeof(struct DNS_HEADER)];
    dns_format(qname, host);


    struct QUESTION *qinfo = (struct QUESTION*)&buf[sizeof(struct DNS_HEADER) + strlen((const char*)qname) + 1];
    qinfo->qtype = htons(1);  // A record
    qinfo->qclass = htons(1); // IN class

    printf("Sending DNS query to %s for %s...\n", dns_server, host);

    int query_len = sizeof(struct DNS_HEADER) + strlen((const char*)qname) + 1 + sizeof(struct QUESTION);
    printf("Query length: %d\n", query_len);
    print_bytes(buf, query_len);

    if (sendto(sock, (char*)buf, query_len, 0, (struct sockaddr*)&dest, sizeof(dest)) < 0) {
        perror("sendto failed");
        close(sock);
        return 1;
    }


    printf("Receiving response...\n");
    int i = sizeof(dest);
    int received = recvfrom(sock, (char*)buf, MAX_DNS_SIZE, 0, (struct sockaddr*)&dest, (socklen_t*)&i);
    if (received < 0) {
        perror("recvfrom failed");
        close(sock);
        return 1;

    }

    printf("Received %d bytes\n", received);
    print_bytes(buf, received);

    dns = (struct DNS_HEADER*) buf;
    printf("Response code: %d\n", dns->rcode);
    printf("Answers: %d\n", ntohs(dns->ans_count));
    printf("Authoritative: %d\n", ntohs(dns->auth_count));
    printf("Additional: %d\n", ntohs(dns->add_count));

    close(sock);
    return 0;
}
