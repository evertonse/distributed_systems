#if SHELL_SCHIPT
self="$(realpath "$0")"
exe="${self%.*}.bin"  # Remove any extension and add .bin

exec_line="gcc \"$self\" -o \"$exe\" -lglut -ldl -Isrc/ -Idemos/ -Ivendor/ -Ivendor/glad/include/ -Ivendor/stb/include/ -Ivendor/noise/ -O3 -Ofast -Wno-write-strings -fomit-frame-pointer -flto"
echo "exec: $exec_line"

eval $exec_line

if [ $? -eq 0 ]; then
    echo "Compile succeeded: $self --> $exe"
    echo "Running: $exe"
    eval $exe
    exit $?
else
    echo "Compile error"
    exit 1
fi
#endif


#include <sys/wait.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/eventfd.h>
#include <string.h>
#include <stdint.h>
#include <errno.h>

#define BUFFER_SIZE 100

int main() {
    // Create an eventfd

    int efd = eventfd(0, 0);
    if (efd == -1) {
        perror("eventfd");
        exit(EXIT_FAILURE);

    }

    // Fork a process
    pid_t pid = fork();
    if (pid < 0) {
        perror("fork");
        exit(EXIT_FAILURE);
    }

    if (pid == 0) {
        // Child process
        uint64_t u;
        char message[BUFFER_SIZE];

        while (1) {
            // Wait for the eventfd to be signaled
            ssize_t s = read(efd, &u, sizeof(uint64_t));
            if (s != sizeof(uint64_t)) {
                perror("read");
                exit(EXIT_FAILURE);
            }

            // Read the message from stdin
            printf("Child received signal: %llu\n", (unsigned long long)u);
            printf("Enter a message: ");
            fgets(message, BUFFER_SIZE, stdin);
            message[strcspn(message, "\n")] = 0; // Remove newline character

            // Print the message
            printf("Child received message: %s\n", message);

            // If the message is "exit", break the loop
            if (strcmp(message, "exit") == 0) {
                break;
            }
        }


        close(efd); // Close the eventfd

    } else {
        // Parent process
        while (1) {
            // Notify the child process
            uint64_t u = 1; // Event value
            ssize_t s = write(efd, &u, sizeof(uint64_t));
            if (s != sizeof(uint64_t)) {
                perror("write");
                exit(EXIT_FAILURE);
            }


            // Wait for the child to process the message
            sleep(1); // Simulate some delay
        }

        // Wait for the child process to finish
        wait(NULL);
        close(efd); // Close the eventfd
    }

    return 0;
}

