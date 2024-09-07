#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dbus/dbus.h>


#define SERVICE_NAME "com.example.Greeter"

#define OBJECT_PATH "/com/example/Greeter"
#define INTERFACE_NAME "com.example.GreeterInterface"

void send_greeting(DBusMessage *msg, DBusConnection *conn) {

    DBusMessage *reply;
    char *greeting = "Hello, D-Bus!";


    // Create a reply message
    reply = dbus_message_new_method_return(msg);
    if (!reply) {
        fprintf(stderr, "Out of memory!\n");
        return;
    }

    // Append the greeting string to the reply
    dbus_message_append_args(reply, DBUS_TYPE_STRING, &greeting, DBUS_TYPE_INVALID);

    // Send the reply
    if (!dbus_connection_send(conn, reply, NULL)) {
        fprintf(stderr, "Out of memory!\n");
    }

    dbus_message_unref(reply);
}

int main() {
    DBusError err;
    DBusConnection *conn;

    // Initialize the error
    dbus_error_init(&err);

    // Connect to the session bus
    conn = dbus_bus_get(DBUS_BUS_SESSION, &err);
    if (dbus_error_is_set(&err)) {
        fprintf(stderr, "Connection Error (%s)\n", err.message);
        dbus_error_free(&err);
        return 1;
    }
    if (conn == NULL) {
        return 1;
    }

    // Request a name on the bus
    int ret = dbus_bus_request_name(conn, SERVICE_NAME, DBUS_NAME_FLAG_REPLACE_EXISTING, &err);
    if (dbus_error_is_set(&err)) {
        fprintf(stderr, "Name Error (%s)\n", err.message);
        dbus_error_free(&err);
        return 1;
    }
    if (ret != DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER) {
        fprintf(stderr, "Not primary owner of the name\n");
        return 1;
    }

    // Main loop
    while (1) {

        // Process incoming messages
        dbus_connection_read_write(conn, 0);
        DBusMessage *msg = dbus_connection_pop_message(conn);
        if (msg) {
            // Check if the message is a method call
            if (dbus_message_is_method_call(msg, INTERFACE_NAME, "GetGreeting")) {
                send_greeting(msg, conn);

            }
            dbus_message_unref(msg);
        }
    }

    // Clean up
    dbus_connection_unref(conn);
    return 0;
}

