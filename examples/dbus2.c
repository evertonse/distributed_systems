#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dbus/dbus.h>

#define SERVICE_NAME "com.example.Service"

#define OBJECT_PATH "/com/example/Service"
#define INTERFACE_NAME "com.example.ServiceInterface"

void handle_method_call(DBusConnection *conn, DBusMessage *msg) {
    const char *method_name;
    char *name;
    DBusMessageIter args;

    method_name = dbus_message_get_member(msg);
    if (strcmp(method_name, "greet") == 0) {
        dbus_message_iter_init(msg, &args);
        dbus_message_iter_get_basic(&args, &name);


        // Create a response message
        DBusMessage *reply;
        reply = dbus_message_new_method_return(msg);
        char response[256];
        snprintf(response, sizeof(response), "Hello, %s!", name);
        dbus_message_append_args(reply, DBUS_TYPE_STRING, &response);

        // Send the response
        dbus_connection_send(conn, reply, NULL);
        dbus_message_unref(reply);
    }
}

int main() {
    DBusError error;
    DBusConnection *conn;

    // Initialize the error
    dbus_error_init(&error);

    // Connect to the session bus
    conn = dbus_bus_get(DBUS_BUS_SESSION, &error);
    if (dbus_error_is_set(&error)) {
        fprintf(stderr, "Connection Error (%s)\n", error.message);
        dbus_error_free(&error);
        return 1;
    }

    // Request a name on the bus
    int ret = dbus_bus_request_name(conn, SERVICE_NAME, DBUS_NAME_FLAG_REPLACE_EXISTING, &error);
    if (dbus_error_is_set(&error)) {
        fprintf(stderr, "Name Error (%s)\n", error.message);
        dbus_error_free(&error);
        return 1;
    }

    // Main loop
    while (1) {
        dbus_connection_read_write(conn, 0);
        DBusMessage *msg = dbus_connection_pop_message(conn);
        if (msg != NULL) {
            if (dbus_message_is_method_call(msg, INTERFACE_NAME, "greet")) {
                handle_method_call(conn, msg);
            }

            dbus_message_unref(msg);
        }
    }

    return 0;
}

