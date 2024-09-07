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
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <arpa/inet.h>

void log_address_info(struct addrinfo *res) {
    char ipstr[INET6_ADDRSTRLEN]; // Buffer for IP address string
    struct addrinfo *p;

    printf("Received address information:\n");
    for (p = res; p != NULL; p = p->ai_next) {
        printf("  Address Family: %s\n", (p->ai_family == AF_INET) ? "AF_INET (IPv4)" : "AF_INET6 (IPv6)");
        printf("  Socket Type: %s\n", (p->ai_socktype == SOCK_STREAM) ? "SOCK_STREAM (TCP)" : "SOCK_DGRAM (UDP)");
        printf("  Protocol: %d\n", p->ai_protocol);
        printf("  Address Length: %d\n", p->ai_addrlen);

        // Get the pointer to the address itself
        void *addr;
        if (p->ai_family == AF_INET) { // IPv4
            struct sockaddr_in *ipv4 = (struct sockaddr_in *)p->ai_addr;
            addr = &(ipv4->sin_addr);
        } else { // IPv6
            struct sockaddr_in6 *ipv6 = (struct sockaddr_in6 *)p->ai_addr;
            addr = &(ipv6->sin6_addr);
        }

        // Convert the IP to a string and print it
        inet_ntop(p->ai_family, addr, ipstr, sizeof ipstr);
        printf("  Resolved IP address: %s\n", ipstr);
    }
}

int main() {
    struct addrinfo hints, *res;

    int status;
    // const char *hostname = "www.facebook.com";
    const char *hostname = "www.youtube.com.";

    // Set up hints for getaddrinfo
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC; // AF_INET or AF_INET6 to force version
    hints.ai_socktype = SOCK_STREAM; // TCP stream sockets

    // Iteratively resolve the hostname
    for (int attempt = 1; attempt <= 3; attempt++) {
        printf("Attempt %d to resolve hostname: %s\n", attempt, hostname);


        // Resolve the hostname
        if ((status = getaddrinfo(hostname, NULL, &hints, &res)) != 0) {
            fprintf(stderr, "getaddrinfo error: %s\n", gai_strerror(status));
            return 1;
        }

        // Log the received address information
        log_address_info(res);

        // Free the linked list
        freeaddrinfo(res);

        // Simulate waiting for a while before the next attempt
        printf("Waiting before the next attempt...\n");
        // sleep(2); // Sleep for 2 seconds before the next attempt
    }

    return 0;
}

