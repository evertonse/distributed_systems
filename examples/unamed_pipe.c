
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
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>

#define BUFFER_SIZE 100

int main() {
    int pipefd[2]; // Array to hold the read and write file descriptors
    char buffer[BUFFER_SIZE];

    // Create an unnamed pipe
    if (pipe(pipefd) == -1) {
        perror("pipe");
        exit(EXIT_FAILURE);
    }


    // Fork a process
    pid_t pid = fork();
    if (pid < 0) {
        perror("fork");
        exit(EXIT_FAILURE);
    }

    if (pid == 0) {
        // Child process: Reader
        close(pipefd[1]); // Close the write end of the pipe

        // Read messages from the pipe
        while (1) {
            ssize_t bytesRead = read(pipefd[0], buffer, BUFFER_SIZE);
            if (bytesRead > 0) {
                buffer[bytesRead] = '\0'; // Null-terminate the string
                if (strcmp(buffer, "exit") == 0) {
                    break; // Exit if 'exit' is received
                }
                printf("Child received: %s\n", buffer);
            }
        }

        close(pipefd[0]); // Close the read end of the pipe
    } else {

        // Parent process: Writer
        close(pipefd[0]); // Close the read end of the pipe

        // Write messages to the pipe
        while (1) {
            printf("Enter a message (type 'exit' to quit): ");
            fgets(buffer, BUFFER_SIZE, stdin);
            buffer[strcspn(buffer, "\n")] = 0; // Remove newline character

            if (strcmp(buffer, "exit") == 0) {
                write(pipefd[1], buffer, strlen(buffer) + 1); // Send 'exit' to child
                break; // Exit the loop
            }

            write(pipefd[1], buffer, strlen(buffer) + 1); // Write message to pipe
        }

        close(pipefd[1]); // Close the write end of the pipe
        wait(NULL); // Wait for the child process to finish
    }

    return 0;
}

