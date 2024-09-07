import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;

@DBusInterfaceName("com.example.ServiceInterface")
interface ServiceInterface extends DBusInterface {
    void sendMessage(String message);

}

public class DBusServiceClient implements ServiceInterface {
    public void sendMessage(String message) {
        System.out.println("Received message: " + message);
        // Here you can emit a signal or perform some action based on the message
    }

    public void serve() {
        try {
            DBusConnection connection = DBusConnection.getConnection(DBusConnection.SESSION_BUS);
            connection.exportObject("/com/example/Service", this);
            System.out.println("Service is running...");
            // Keep the service running
            Thread.sleep(Long.MAX_VALUE);
        } catch (DBusException | InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void client() {
        try {
            DBusConnection connection = DBusConnection.getConnection(DBusConnection.SESSION_BUS);
            ServiceInterface service = connection.getRemoteObject("com.example.ServiceInterface", "/com/example/Service", ServiceInterface.class);

            // Send a message to the service
            String message = "Hello from the client!";
            service.sendMessage(message);

            System.out.println("Sent message: " + message);
        } catch (DBusException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        DBusServiceClient dbusServiceClient = new DBusServiceClient();

        if (args.length > 0 && args[0].equals("serve")) {
            dbusServiceClient.serve();
        } else {
            dbusServiceClient.client();
        }
    }
}

