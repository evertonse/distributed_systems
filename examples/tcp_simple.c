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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/wait.h>

#define PORT 6969
#define BUFFER_SIZE 1024

// https://www.geeksforgeeks.org/tcp-server-client-implementation-in-c/

// Good as shit: https://www.cs.dartmouth.edu/~campbell/cs60/socketprogramming.html#x1-60011
void handle_client(int new_socket, struct sockaddr_in address) {
    char buffer[BUFFER_SIZE] = {0};

    int valread;

    // Print client IP address
    char client_ip[INET_ADDRSTRLEN];
    inet_ntop(AF_INET, &address.sin_addr, client_ip, sizeof(client_ip));
    printf("Connection from %s:%d\n", client_ip, ntohs(address.sin_port));

    // Read data from the client
    while ((valread = read(new_socket, buffer, BUFFER_SIZE)) > 0) {
        buffer[valread] = '\0'; // Null-terminate the string
        printf("Received from %s: %s\n", client_ip, buffer);
    }


    // Close the socket when done
    close(new_socket);
    printf("Connection closed from %s:%d\n", client_ip, ntohs(address.sin_port));
}


int server() {
    int server_fd, new_socket;

    struct sockaddr_in address, client_address;
    int opt = 1;
    int addrlen = sizeof(address);
    int client_address_len = sizeof(client_address);

    // Create socket file descriptor
    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        perror("socket failed");
        exit(EXIT_FAILURE);
    }


    // Attach socket to the port
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt))) {
        perror("setsockopt");
        exit(EXIT_FAILURE);
    }

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(PORT);


    // Bind the socket to the address
    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        perror("bind failed");
        exit(EXIT_FAILURE);

    }

    // Start listening for connections

    if (listen(server_fd, 3) < 0) {
        perror("listen");
        exit(EXIT_FAILURE);
    }

    printf("Server listening on port %d\n", PORT);

    while (1) {
        // Accept a new connection
        // If the listen queue is empty of connection requests and O_NONBLOCK is  not
        // set  on  the  file descriptor for the socket, accept() shall block until a
        // connection is present. If the listen() queue is empty  of  connection  re‐
        // quests  and  O_NONBLOCK  is set on the file descriptor for the socket, ac‐
        // cept() shall fail and set errno to [EAGAIN] or [EWOULDBLOCK].

        if ((new_socket = accept(server_fd, (struct sockaddr *)&client_address, (socklen_t*)&client_address_len)) < 0) {
            perror("accept");
            exit(EXIT_FAILURE);
        }

        // Create a new process to handle the client
        if (fork() == 0) {
            // In child process
            close(server_fd); // Close the listening socket in the child
            handle_client(new_socket, address);
            exit(0); // Exit child process after handling the client
        } else {
            // In parent process
            close(new_socket); // Close the connected socket in the parent
        }

        // Clean up zombie processes

        while (waitpid(-1, NULL, WNOHANG) > 0);
    }

    return 0;
}

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>

#define BUFFER_SIZE 1024

int client() {
    int sock = 0;
    struct sockaddr_in serv_addr;
    char buffer[BUFFER_SIZE] = {0};

    // Create socket
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        printf("\n Socket creation error \n");
        return -1;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);

    // Convert IPv4 and IPv6 addresses from text to binary form
    if (inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) <= 0) {
        printf("\nInvalid address/ Address not supported \n");
        return -1;
    }

    // The client does not have to call bind() the kernel will choose both an ephemeral port and the source IP if necessary.
    // Connect to the server
    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        printf("\nConnection Failed \n");
        return -1;
    }


    while (1) {
        printf("Enter message: ");

        fgets(buffer, BUFFER_SIZE, stdin);
        send(sock, buffer, strlen(buffer), 0);
    }

    close(sock);

    return 0;

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
