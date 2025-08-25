package com.wifiguard.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Simple test client để test WifiGuard Server
 */
public class SimpleTestClient {
    // Constants
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9099;
    private static final int SOCKET_TIMEOUT = 60000; // 60 giây
    private static final int BUFFER_SIZE = 32768; // 32KB
    private static final String ENCODING = "UTF-8";
    
    public static void main(String[] args) {
        try {
            // Fix encoding issue
            System.setProperty("file.encoding", ENCODING);
            System.setProperty("console.encoding", ENCODING);
            
            System.out.println("=== Simple Test Client ===");
            
            Socket socket = createAndConfigureSocket();
            System.out.println("Ket noi thanh cong!");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), ENCODING));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), ENCODING), true);
            
            // Test các lệnh
            testWelcomeMessage(reader);
            testCommand(reader, writer, "STATUS", "Testing STATUS command");
            testCommand(reader, writer, "LIST", "Testing LIST command (network devices)");
            testCommand(reader, writer, "ALLOWLIST", "Testing ALLOWLIST command (allowed devices)");
            testCommand(reader, writer, "ADD 00:1B:44:AA:BB:CC NewDevice 192.168.1.200", "Testing ADD command voi MAC moi");
            testCommand(reader, writer, "ADD 00:1B:44:01:02:03 TestDevice 192.168.1.100", "Testing ADD command voi MAC da ton tai");
            testCommand(reader, writer, "DEL 00:1B:44:AA:BB:CC", "Testing DEL command");
            
            // Đóng kết nối
            socket.close();
            System.out.println("Test hoan thanh!");
            
        } catch (Exception e) {
            System.err.println("Loi: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Tạo và cấu hình socket
     */
    private static Socket createAndConfigureSocket() throws Exception {
        Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
        
        // Cấu hình socket
        socket.setSoTimeout(SOCKET_TIMEOUT);
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        socket.setReuseAddress(true);
        socket.setReceiveBufferSize(BUFFER_SIZE);
        socket.setSendBufferSize(BUFFER_SIZE);
        
        return socket;
    }
    
    /**
     * Test welcome message
     */
    private static void testWelcomeMessage(BufferedReader reader) throws Exception {
        // Đọc welcome message
        String welcome = reader.readLine();
        System.out.println("Server: " + welcome);
        System.out.println("Welcome length: " + welcome.length());
        
        // Đọc confirmation
        String confirmation = reader.readLine();
        System.out.println("Confirmation: " + confirmation);
    }
    
    /**
     * Test một lệnh cụ thể
     */
    private static void testCommand(BufferedReader reader, PrintWriter writer, String command, String description) throws Exception {
        System.out.println("\n--- " + description + " ---");
        writer.println(command);
        System.out.println("Da gui: " + command);
        
        String response = reader.readLine();
        if (response != null) {
            System.out.println("Server response: " + response);
            System.out.println("Response length: " + response.length());
        }
        
        // Đọc confirmation
        String confirmation = reader.readLine();
        System.out.println("Confirmation: " + confirmation);
    }
}



