import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Test client đơn giản để debug server
 */
public class SimpleTestClient {
    public static void main(String[] args) {
        try {
            // Fix encoding issue
            System.setProperty("file.encoding", "UTF-8");
            System.setProperty("console.encoding", "UTF-8");
            
            System.out.println("=== Simple Test Client ===");
            
            Socket socket = new Socket("localhost", 9099);
            System.out.println("Ket noi thanh cong!");
            
            // Cấu hình socket tối ưu
            socket.setSoTimeout(60000); // 60 giây timeout
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            socket.setReuseAddress(true);
            
            // Tăng buffer size
            socket.setReceiveBufferSize(32768); // 32KB
            socket.setSendBufferSize(32768);    // 32KB
            
            // Sử dụng UTF-8 encoding
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            
            // Đọc welcome message
            String welcome = reader.readLine();
            System.out.println("Server: " + welcome);
            System.out.println("Welcome length: " + (welcome != null ? welcome.length() : "null"));
            
            // Đọc confirmation
            String confirmation = reader.readLine();
            System.out.println("Confirmation: " + confirmation);
            
            // Test lệnh STATUS
            System.out.println("\n--- Testing STATUS command ---");
            writer.println("STATUS");
            System.out.println("Da gui: STATUS");
            
            String response = reader.readLine();
            if (response != null) {
                System.out.println("Server response: " + response);
                System.out.println("Response length: " + response.length());
            } else {
                System.out.println("Khong co response tu server!");
            }
            
            // Đọc confirmation
            confirmation = reader.readLine();
            System.out.println("Confirmation: " + confirmation);
            
            // Test lệnh LIST
            System.out.println("\n--- Testing LIST command ---");
            writer.println("LIST");
            System.out.println("Da gui: LIST");
            
            response = reader.readLine();
            if (response != null) {
                System.out.println("Server response: " + response);
                System.out.println("Response length: " + response.length());
            } else {
                System.out.println("Khong co response tu server!");
            }
            
            // Đọc confirmation
            confirmation = reader.readLine();
            System.out.println("Confirmation: " + confirmation);
            
            // Test lệnh ADD với MAC mới
            System.out.println("\n--- Testing ADD command với MAC mới ---");
            writer.println("ADD 00:1B:44:AA:BB:CC NewDevice 192.168.1.200");
            System.out.println("Da gui: ADD 00:1B:44:AA:BB:CC NewDevice 192.168.1.200");
            
            response = reader.readLine();
            if (response != null) {
                System.out.println("Server response: " + response);
                System.out.println("Response length: " + response.length());
            } else {
                System.out.println("Khong co response tu server!");
            }
            
            // Đọc confirmation
            confirmation = reader.readLine();
            System.out.println("Confirmation: " + confirmation);
            
            // Test lệnh ADD với MAC đã tồn tại
            System.out.println("\n--- Testing ADD command với MAC đã tồn tại ---");
            writer.println("ADD 00:1B:44:01:02:03 TestDevice 192.168.1.100");
            System.out.println("Da gui: ADD 00:1B:44:01:02:03 TestDevice 192.168.1.100");
            
            response = reader.readLine();
            if (response != null) {
                System.out.println("Server response: " + response);
                System.out.println("Response length: " + response.length());
            } else {
                System.out.println("Khong co response tu server!");
            }
            
            // Đọc confirmation
            confirmation = reader.readLine();
            System.out.println("Confirmation: " + confirmation);
            
            // Test lệnh DEL
            System.out.println("\n--- Testing DEL command ---");
            writer.println("DEL 00:1B:44:AA:BB:CC");
            System.out.println("Da gui: DEL 00:1B:44:AA:BB:CC");
            
            response = reader.readLine();
            if (response != null) {
                System.out.println("Server response: " + response);
                System.out.println("Response length: " + response.length());
            } else {
                System.out.println("Khong co response tu server!");
            }
            
            // Đọc confirmation
            confirmation = reader.readLine();
            System.out.println("Confirmation: " + confirmation);
            
            // Đóng kết nối
            writer.println("QUIT");
            socket.close();
            System.out.println("Test hoan thanh!");
            
        } catch (Exception e) {
            System.err.println("Loi: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
