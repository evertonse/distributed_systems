#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <mqueue.h>
#include <sys/types.h>
#include <sys/wait.h>

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


#define QUEUE_NAME "/my_message_queue"
#define MAX_MSG_SIZE 1024

int main() {
    pid_t pid;
    mqd_t queue;
    struct mq_attr attr;
    char msg[MAX_MSG_SIZE];

    // Create the message queue
    attr.mq_maxmsg = 10;
    attr.mq_msgsize = MAX_MSG_SIZE;
    queue = mq_open(QUEUE_NAME, O_CREAT | O_RDWR, 0644, &attr);

    if (queue == (mqd_t)-1) {
        perror("mq_open");
        return 1;
    }


    // Fork the process
    pid = fork();
    if (pid == -1) {
        perror("fork");
        mq_close(queue);
        mq_unlink(QUEUE_NAME);
        return 1;
    }

    if (pid == 0) {

        // Child process: Receive messages from the queue
        printf("Child process (PID: %d) started.\n", getpid());
        while (1) {
            if (mq_receive(queue, msg, MAX_MSG_SIZE, NULL) == -1) {
                perror("mq_receive");
                break;
            }
            printf("Child process received message: %s\n", msg);
        }
        mq_close(queue);
    } else {
        // Parent process: Send messages to the queue

        printf("Parent process (PID: %d) started.\n", getpid());
        sprintf(msg, "Hello from parent process!");
        if (mq_send(queue, msg, strlen(msg) + 1, 0) == -1) {
            perror("mq_send");
            mq_close(queue);
            mq_unlink(QUEUE_NAME);
            return 1;
        }
        printf("Parent process sent message: %s\n", msg);
        wait(NULL);
        mq_close(queue);
        mq_unlink(QUEUE_NAME);
    }


    return 0;
}


