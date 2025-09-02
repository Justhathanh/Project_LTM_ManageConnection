import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class TestDeleteUI {
    public static void main(String[] args) {
        try {
            System.out.println("Testing DELETE command for UI...");
            
            // Connect to server
            Socket socket = new Socket("127.0.0.1", 9099);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            
            System.out.println("Connected to server");
            
            // Read welcome message
            String welcome = in.readLine();
            System.out.println("Welcome: " + welcome);
            
            // Test 1: List devices to see what's available
            System.out.println("\n=== Test 1: LIST devices ===");
            out.println("LIST");
            out.flush();
            
            String response = readResponse(in);
            System.out.println("LIST Response:");
            System.out.println(response);
            
            // Test 2: Check allowlist
            System.out.println("\n=== Test 2: Check ALLOWLIST ===");
            out.println("ALLOWLIST");
            out.flush();
            
            response = readResponse(in);
            System.out.println("ALLOWLIST Response:");
            System.out.println(response);
            
            // Test 3: Add a device first (if not in allowlist)
            System.out.println("\n=== Test 3: ADD device to allowlist ===");
            out.println("ADD 2E:68:47:0E:72:F9"); // Use a MAC from the device list
            out.flush();
            
            response = readResponse(in);
            System.out.println("ADD Response:");
            System.out.println(response);
            
            // Test 4: Now try to delete the device
            System.out.println("\n=== Test 4: DELETE device ===");
            out.println("DEL 2E:68:47:0E:72:F9");
            out.flush();
            
            response = readResponse(in);
            System.out.println("DELETE Response:");
            System.out.println(response);
            
            // Test 5: Check allowlist again
            System.out.println("\n=== Test 5: Check ALLOWLIST after delete ===");
            out.println("ALLOWLIST");
            out.flush();
            
            response = readResponse(in);
            System.out.println("ALLOWLIST Response after delete:");
            System.out.println(response);
            
            // Close connection
            out.println("QUIT");
            socket.close();
            
            System.out.println("\nTest completed!");
            
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String readResponse(BufferedReader in) throws IOException {
        StringBuilder response = new StringBuilder();
        String line;
        int lineCount = 0;
        int maxLines = 20;
        
        while ((line = in.readLine()) != null && lineCount < maxLines) {
            response.append(line).append("\n");
            lineCount++;
            
            // Stop if we see end markers
            if (line.contains("+--------------------------------------------------") || 
                line.contains("End of response") ||
                line.trim().isEmpty()) {
                break;
            }
        }
        
        return response.toString().trim();
    }
}
