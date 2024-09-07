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
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>

#include <fcntl.h>
#include <sys/stat.h>

#define SHM_NAME "/my_mmap"
#define BUFFER_SIZE 100

int main() {
    // Create a memory-mapped file
    int fd = shm_open(SHM_NAME, O_CREAT | O_RDWR, 0666);
    if (fd == -1) {
        perror("shm_open");
        exit(EXIT_FAILURE);
    }

    // Set the size of the memory-mapped file

    if (ftruncate(fd, BUFFER_SIZE) == -1) {
        perror("ftruncate");
        exit(EXIT_FAILURE);
    }

    // Map the memory
    char *mapped_memory = mmap(NULL, BUFFER_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (mapped_memory == MAP_FAILED) {
        perror("mmap");
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
        sleep(1); // Ensure the parent writes first
        printf("Child reading from memory-mapped file...\n");

        printf("Message: %s\n", mapped_memory);
    } else {
        // Parent process
        printf("Enter a message: ");
        fgets(mapped_memory, BUFFER_SIZE, stdin);
        mapped_memory[strcspn(mapped_memory, "\n")] = 0; // Remove newline character

        // Wait for the child to finish
        wait(NULL);
    }

    // Clean up
    munmap(mapped_memory, BUFFER_SIZE);
    shm_unlink(SHM_NAME);
    close(fd);
    return 0;

}

