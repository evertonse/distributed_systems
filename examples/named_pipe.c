
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


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#define FIFO_NAME "my_fifo"
#define BUFFER_SIZE 100

void create_fifo() {
    // Create a named pipe (FIFO)
    if (mkfifo(FIFO_NAME, 0666) == -1) {
        perror("mkfifo");
        exit(EXIT_FAILURE);
    }
}

void write_to_fifo() {
    int fd;
    char buffer[BUFFER_SIZE];

    // Open the FIFO for writing
    fd = open(FIFO_NAME, O_WRONLY);
    if (fd == -1) {

        perror("open");
        exit(EXIT_FAILURE);
    }

    // Write messages to the FIFO
    while (1) {
        printf("Enter a message (type 'exit' to quit): ");
        fgets(buffer, BUFFER_SIZE, stdin);
        buffer[strcspn(buffer, "\n")] = 0; // Remove newline character


        if (strcmp(buffer, "exit") == 0) {
            break; // Exit the loop if the user types 'exit'
        }

        write(fd, buffer, strlen(buffer) + 1); // Write message to FIFO
    }

    close(fd);
}


void read_from_fifo() {
    int fd;
    char buffer[BUFFER_SIZE];

    // Open the FIFO for reading

    fd = open(FIFO_NAME, O_RDONLY);
    if (fd == -1) {
        perror("open");

        exit(EXIT_FAILURE);
    }

    // Read messages from the FIFO
    while (1) {
        read(fd, buffer, BUFFER_SIZE);
        if (strcmp(buffer, "exit") == 0) {
            break; // Exit the loop if 'exit' is received
        }
        printf("Received: %s\n", buffer);
    }

    close(fd);
}

int main() {
    // Create the FIFO
    create_fifo();
    printf("Hello world\n");
    
    // Fork a process to handle reading and writing
    pid_t pid = fork();
    if (pid < 0) {
        perror("fork");
        exit(EXIT_FAILURE);
    }

    if (pid == 0) {
        // Child process: Reader
        read_from_fifo();
    } else {
        // Parent process: Writer
        write_to_fifo();
        wait(NULL); // Wait for the child process to finish
    }


    // Remove the FIFO

    unlink(FIFO_NAME);
    return 0;
}

