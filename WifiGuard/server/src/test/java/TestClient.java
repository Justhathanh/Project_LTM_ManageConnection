import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Simple test client for WifiGuard Server
 * Use this instead of telnet on Windows
 */
public class TestClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9099;
    
    public static void main(String[] args) {
        System.out.println("=== WifiGuard Server Test Client ===");
        System.out.println("Connecting to " + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println();
        
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {
            
            System.out.println("Connected to server successfully!");
            System.out.println("Available commands: LIST, ADD, DEL, STATUS, QUIT");
            System.out.println("Example: ADD 00:1B:44:01:02:03 TestDevice 192.168.1.100");
            System.out.println();
            
            // Start response reader thread
            Thread responseReader = new Thread(() -> {
                try {
                    String response;
                    while ((response = reader.readLine()) != null) {
                        System.out.println("Server: " + response);
                    }
                } catch (IOException e) {
                    System.out.println("Connection closed by server");
                }
            });
            responseReader.setDaemon(true);
            responseReader.start();
            
            // Command input loop
            System.out.print("Enter command: ");
            while (scanner.hasNextLine()) {
                String command = scanner.nextLine().trim();
                
                if (command.isEmpty()) {
                    System.out.print("Enter command: ");
                    continue;
                }
                
                if ("QUIT".equalsIgnoreCase(command)) {
                    writer.println(command);
                    System.out.println("Disconnecting...");
                    break;
                }
                
                // Send command to server
                writer.println(command);
                System.out.println("Sent: " + command);
                
                // Small delay to see response
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                System.out.print("Enter command: ");
            }
            
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            System.err.println("Make sure the server is running on " + SERVER_HOST + ":" + SERVER_PORT);
        }
        
        System.out.println("Test client closed.");
    }
}
