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
#include <signal.h>
#include <string.h>

#define BUFFER_SIZE 100

// Global variable to hold the message
char message[BUFFER_SIZE];

// Signal handler for the child process
void handle_signal(int sig) {
    printf("Child received signal: %d\n", sig);
    printf("Message: %s\n", message);
}

int main() {
    // Set up the signal handler in the child process
    signal(SIGUSR1, handle_signal);


    // Fork a process
    pid_t pid = fork();
    if (pid < 0) {
        perror("fork");
        exit(EXIT_FAILURE);
    }

    if (pid == 0) {

        // Child process
        while (1) {
            pause(); // Wait for a signal
        }
    } else {
        // Parent process
        while (1) {
            printf("Enter a message (type 'exit' to quit): ");
            fgets(message, BUFFER_SIZE, stdin);
            message[strcspn(message, "\n")] = 0; // Remove newline character

            if (strcmp(message, "exit") == 0) {
                kill(pid, SIGTERM); // Terminate the child process
                break; // Exit the loop
            }

            // Send a signal to the child process
            kill(pid, SIGUSR1);
        }


        // Wait for the child process to finish

        wait(NULL);
    }


    return 0;

}

