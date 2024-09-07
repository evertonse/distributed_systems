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

#include <arpa/inet.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#define DNS_PORT 53
#define MAX_PACKET_SIZE 512

int main_second_attempt(int argc, char *argv[]);
// DNS header structure
typedef struct {
  unsigned short id;
  unsigned short flags;
  unsigned short qdcount;
  unsigned short ancount;
  unsigned short nscount;
  unsigned short arcount;
} dns_header_t;

// DNS question structure
typedef struct {
  char *name;
  unsigned short type;
  unsigned short class;
} dns_question_t;

// DNS resource record structure
typedef struct {
  char *name;
  unsigned short type;
  unsigned short class;
  unsigned int ttl;
  unsigned short rdlength;
  char *rdata;
} dns_rr_t;

void send_dns_query(const char *domain) {
  int sock, len, i;
  struct sockaddr_in server;
  dns_header_t header;
  dns_question_t question;
  char packet[MAX_PACKET_SIZE];

  // Create a UDP socket
  sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if (sock < 0) {
    printf("Failed to create socket\n");
    return;
  }

  // Set up the server address
  memset(&server, 0, sizeof(server));
  server.sin_family = AF_INET;
  server.sin_port = htons(DNS_PORT);
  server.sin_addr.s_addr = inet_addr("8.8.8.8"); // Use Google DNS server

  // Construct the DNS query packet
  memset(&header, 0, sizeof(header));
  header.id = rand() % 65536;
  header.flags = htons(0x0100); // Standard query
  header.qdcount = htons(1);

  question.name = (char *)domain;
  question.type = htons(1);  // A record
  question.class = htons(1); // Internet

  len = sizeof(header) + strlen(domain) + 1 + sizeof(question.type) +
        sizeof(question.class);
  memcpy(packet, &header, sizeof(header));
  memcpy(packet + sizeof(header), domain, strlen(domain) + 1);
  memcpy(packet + sizeof(header) + strlen(domain) + 1, &question.type,
         sizeof(question.type));
  memcpy(packet + sizeof(header) + strlen(domain) + 1 + sizeof(question.type),
         &question.class, sizeof(question.class));

  // Send the DNS query
  sendto(sock, packet, len, 0, (struct sockaddr *)&server, sizeof(server));

  // Receive the DNS response

  len = recvfrom(sock, packet, MAX_PACKET_SIZE, 0, NULL, NULL);
  if (len < 0) {
    printf("Failed to receive DNS response\n");
    close(sock);
    return;
  }

  // Parse the DNS response
  memcpy(&header, packet, sizeof(header));
  printf("Domain: %s\n", domain);
  printf("ID: %d\n", ntohs(header.id));
  printf("Flags: 0x%04x\n", ntohs(header.flags));
  printf("Questions: %d\n", ntohs(header.qdcount));
  printf("Answers: %d\n", ntohs(header.ancount));
  printf("Authority RRs: %d\n", ntohs(header.nscount));
  printf("Additional RRs: %d\n", ntohs(header.arcount));

  // Print the IP addresses
  i = sizeof(header);
  while (i < len) {
    dns_rr_t rr;
    int j, rdlength;

    // Parse the resource record
    rr.name = packet + i;
    i += strlen(rr.name) + 1;
    memcpy(&rr.type, packet + i, sizeof(rr.type));
    rr.type = ntohs(rr.type);

    i += sizeof(rr.type);
    memcpy(&rr.class, packet + i, sizeof(rr.class));
    rr.class = ntohs(rr.class);
    i += sizeof(rr.class);
    memcpy(&rr.ttl, packet + i, sizeof(rr.ttl));
    rr.ttl = ntohl(rr.ttl);
    i += sizeof(rr.ttl);
    memcpy(&rdlength, packet + i, sizeof(rdlength));
    rr.rdlength = ntohs(rdlength);
    i += sizeof(rdlength);
    rr.rdata = packet + i;
    i += rr.rdlength;

    // Print the resource record
    printf("Resource Record:\n");
    printf("  Name: %s\n", rr.name);
    printf("  Type: %d\n", rr.type);
    printf("  Class: %d\n", rr.class);
    printf("  TTL: %u\n", rr.ttl);

    printf("  Data Length: %d\n", rr.rdlength);
    printf("  Data: ");

    // Print the IP address(es)
    if (rr.type == 1) { // A record
      for (j = 0; j < rr.rdlength; j += 4) {
        printf("%s", inet_ntoa(*(struct in_addr *)(rr.rdata + j)));
        if (j + 4 < rr.rdlength) {
          printf(", ");
        }
      }
    } else {

      for (j = 0; j < rr.rdlength; j++) {

        printf("%02X", (unsigned char)rr.rdata[j]);
        if (j + 1 < rr.rdlength) {
          printf(":");
        }
      }
    }
    printf("\n\n");
  }

  close(sock);
}

int main(int argc, char *argv[]) {

  if (1) {
    main_second_attempt(argc, argv);
    return 0;
  }

  char domain[256];

  printf("Enter the domain to look up: ");
  scanf("%s", domain);

  while (1) {
    send_dns_query(domain);
    printf("Press Enter to try again, or Ctrl+C to exit...");
    getchar();
    getchar(); // Consume the newline character
  }

  return 0;
}

#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

// #define DNS_SERVER "8.8.8.8" // Google's public DNS server
#define DNS_SERVER "198.41.0.4"

#define DNS_PORT 53

#define MAX_DNS_SIZE 512

// DNS header structure
struct DNS_HEADER {
  unsigned short id;
  unsigned char rd : 1;
  unsigned char tc : 1;
  unsigned char aa : 1;

  unsigned char opcode : 4;
  unsigned char qr : 1;
  unsigned char rcode : 4;
  unsigned char cd : 1;
  unsigned char ad : 1;
  unsigned char z : 1;
  unsigned char ra : 1;
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

// Pointer to resource record structure
#pragma pack(push, 1)
struct RES_RECORD {
  unsigned char *name;
  struct R_DATA *resource;
  unsigned char *rdata;
};
#pragma pack(pop)

// Function to convert hostname to DNS name format
void change_to_dns_name_format(unsigned char *dns, unsigned char *host) {
  int lock = 0, i;
  strcat((char *)host, ".");
  for (i = 0; i < strlen((char *)host); i++) {
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

// Function to perform DNS query
void dns_query(unsigned char *host) {
  unsigned char buf[MAX_DNS_SIZE], *qname;
  struct sockaddr_in dest;
  struct DNS_HEADER *dns = NULL;
  struct QUESTION *qinfo = NULL;

  int s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);

  dest.sin_family = AF_INET;
  dest.sin_port = htons(DNS_PORT);
  dest.sin_addr.s_addr = inet_addr(DNS_SERVER);

  dns = (struct DNS_HEADER *)&buf;
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

  qname = (unsigned char *)&buf[sizeof(struct DNS_HEADER)];
  change_to_dns_name_format(qname, host);

  qinfo = (struct QUESTION *)&buf[sizeof(struct DNS_HEADER) +
                                  (strlen((const char *)qname) + 1)];
  qinfo->qtype = htons(1);
  qinfo->qclass = htons(1);

  printf("Sending DNS query...\n");
  if (sendto(s, (char *)buf,
             sizeof(struct DNS_HEADER) + (strlen((const char *)qname) + 1) +
                 sizeof(struct QUESTION),
             0, (struct sockaddr *)&dest, sizeof(dest)) < 0) {
    perror("sendto failed");
    return;
  }

  printf("Receiving response...\n");
  int i = sizeof dest;
  if (recvfrom(s, (char *)buf, MAX_DNS_SIZE, 0, (struct sockaddr *)&dest,
               (socklen_t *)&i) < 0) {
    perror("recvfrom failed");
    return;
  }

  dns = (struct DNS_HEADER *)buf;
  unsigned char *reader =
      &buf[sizeof(struct DNS_HEADER) + (strlen((const char *)qname) + 1) +
           sizeof(struct QUESTION)];

  printf("Response received. Parsing...ntohs(dns->ans_count) = %d\n", ntohs(dns->ans_count));
  for (i = 0; i < ntohs(dns->ans_count); i++) {

    struct RES_RECORD answer;
    answer.name = reader;
    reader = reader + strlen((const char *)reader) + 1;
    answer.resource = (struct R_DATA *)(reader);
    reader = reader + sizeof(struct R_DATA);

    if (1 || ntohs(answer.resource->type) == 1) { // If it's an A record
      answer.rdata = (unsigned char *)malloc(ntohs(answer.resource->data_len));
      for (int j = 0; j < ntohs(answer.resource->data_len); j++) {
        answer.rdata[j] = reader[j];
      }
      answer.rdata[ntohs(answer.resource->data_len)] = '\0';
      reader = reader + ntohs(answer.resource->data_len);

      long *p = (long *)answer.rdata;
      struct in_addr addr;
      addr.s_addr = (*p);
      printf("IP Address: %s\n", inet_ntoa(addr));
    }
  }

  close(s);
}

int main_second_attempt(int argc, char *argv[]) {
  if (argc != 2) {
    printf("Usage: %s <hostname>\n", argv[0]);
    return 1;
  }

  unsigned char hostname[100];

  strcpy((char *)hostname, argv[1]);
  dns_query(hostname);

  return 0;
}
