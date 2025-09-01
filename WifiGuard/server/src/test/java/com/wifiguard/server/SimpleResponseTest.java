package com.wifiguard.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Test client để kết nối với WifiGuard server thật
 * và gửi các lệnh để lấy thông tin thiết bị thực tế
 */
public class SimpleResponseTest {
    
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 9099;
    private static final int TIMEOUT_MS = 5000;
    
    public static void main(String[] args) {
        System.out.println("=== TEST KET NOI VOI WIFIGUARD SERVER ===\n");
        
        try {
            // Test ket noi va gui lenh
            testServerConnection();
            
        } catch (Exception e) {
            System.err.println("ERROR: Loi chinh: " + e.getMessage());
            printTroubleshootingTips();
        }
    }
    
    /**
     * Test kết nối với server và gửi các lệnh
     */
    private static void testServerConnection() throws IOException {
        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        
        try {
            // 1. Ket noi voi server
            System.out.println("1. KET NOI VOI SERVER:");
            socket = createConnection();
            System.out.println("OK: Ket noi thanh cong den server: " + socket.getInetAddress() + ":" + socket.getPort());
            
            // 2. Tao input/output streams
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // 3. Test cac lenh
            testListCommand(out, in);
            testStatusCommand(out, in);
            
            // 4. Dong ket noi
            closeConnection(out, socket);
            
        } finally {
            // Dam bao dong resources
            closeResources(out, in, socket);
        }
    }
    
    /**
     * Tạo kết nối socket với timeout
     */
    private static Socket createConnection() throws IOException {
        Socket socket = new Socket();
        socket.setSoTimeout(TIMEOUT_MS);
        socket.connect(new java.net.InetSocketAddress(SERVER_HOST, SERVER_PORT), TIMEOUT_MS);
        return socket;
    }
    
    /**
     * Test lenh LIST
     */
    private static void testListCommand(PrintWriter out, BufferedReader in) throws IOException {
        System.out.println("\n2. GUI LENH LIST:");
        out.println("LIST");
        System.out.println("SENT: Da gui lenh: LIST");
        
        System.out.println("\n3. NHAN RESPONSE TU SERVER:");
        readServerResponse(in, "LIST");
    }
    
    /**
     * Test lenh STATUS
     */
    private static void testStatusCommand(PrintWriter out, BufferedReader in) throws IOException {
        System.out.println("\n4. GUI LENH STATUS:");
        out.println("STATUS");
        System.out.println("SENT: Da gui lenh: STATUS");
        
        System.out.println("\n5. NHAN RESPONSE STATUS:");
        readServerResponse(in, "STATUS");
    }
    
    /**
     * Doc response tu server
     */
    private static void readServerResponse(BufferedReader in, String commandType) throws IOException {
        String response;
        int lineCount = 0;
        
        while ((response = in.readLine()) != null) {
            lineCount++;
            System.out.println("RECV: Server response: " + response);
            
            // Kiem tra ket thuc response
            if (response.contains("END") || response.contains("RESPONSE_SENT")) {
                break;
            }
            
            // Gioi han so dong de tranh vong lap vo han
            if (lineCount > 50) {
                System.out.println("WARN: Da doc " + lineCount + " dong, dung de tranh vong lap vo han");
                break;
            }
        }
        
        if (lineCount == 0) {
            System.out.println("WARN: Khong nhan duoc response tu server cho lenh: " + commandType);
        }
    }
    
    /**
     * Dong ket noi
     */
    private static void closeConnection(PrintWriter out, Socket socket) {
        System.out.println("\n6. DONG KET NOI:");
        try {
            if (out != null) {
                out.println("QUIT");
                System.out.println("SENT: Da gui lenh: QUIT");
            }
            System.out.println("OK: Da dong ket noi");
        } catch (Exception e) {
            System.err.println("WARN: Loi khi dong ket noi: " + e.getMessage());
        }
    }
    
    /**
     * Dong tat ca resources
     */
    private static void closeResources(PrintWriter out, BufferedReader in, Socket socket) {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("WARN: Loi khi dong resources: " + e.getMessage());
        }
    }
    
    /**
     * In huong dan khac phuc su co
     */
    private static void printTroubleshootingTips() {
        System.out.println("\nTIPS: HUONG DAN KHAC PHUC:");
        System.out.println("1. Dam bao server dang chay tren port " + SERVER_PORT);
        System.out.println("2. Chay lenh: java -cp \"target/classes\" com.wifiguard.server.ServerMain");
        System.out.println("3. Kiem tra firewall va antivirus");
        System.out.println("4. Kiem tra log server: Get-Content server_output.log");
    }
}
