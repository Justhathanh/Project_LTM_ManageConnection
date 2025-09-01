package com.wifiguard.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Simple client để test WifiGuard Server
 */
public class SimpleClient {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 9099;
    private static final int TIMEOUT_MS = 15000; // Tăng timeout lên 15 giây
    
    public static void main(String[] args) {
        System.out.println("=== SIMPLE CLIENT TEST ===");
        
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(TIMEOUT_MS);
            
            // Setup streams
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            
            System.out.println("✅ Kết nối thành công đến server: " + SERVER_HOST + ":" + SERVER_PORT);
            
            // Đọc welcome message
            String welcome = reader.readLine();
            if (welcome != null) {
                System.out.println("📨 Server: " + welcome);
            }
            
            // Test các lệnh
            testCommands(writer, reader);
            
        } catch (Exception e) {
            System.err.println("❌ Lỗi kết nối: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testCommands(PrintWriter writer, BufferedReader reader) throws IOException {
        String[] commands = {"LIST", "STATUS", "ALLOWLIST"};
        
        for (String command : commands) {
            System.out.println("\n🔹 Gửi lệnh: " + command);
            writer.println(command);
            
            // Đọc response với timeout ngắn hơn
            System.out.println("📥 Đang đọc response...");
            String response;
            int lineCount = 0;
            int maxLines = 15; // Giới hạn số dòng đọc
            
            while ((response = reader.readLine()) != null && lineCount < maxLines) {
                System.out.println("📥 " + response);
                lineCount++;
                
                // Dừng nếu gặp dòng trống hoặc kết thúc response
                if (response.trim().isEmpty() || 
                    response.contains("+------------------------------------------------------------") ||
                    lineCount >= maxLines) {
                    System.out.println("✅ Đã đọc xong response cho lệnh: " + command);
                    break;
                }
            }
            
            // Đợi một chút giữa các lệnh
            try {
                Thread.sleep(500); // Giảm thời gian chờ
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Gửi lệnh QUIT
        System.out.println("\n🔹 Gửi lệnh: QUIT");
        writer.println("QUIT");
        System.out.println("👋 Đã gửi lệnh thoát");
        
        // Đọc response cuối
        try {
            String quitResponse = reader.readLine();
            if (quitResponse != null) {
                System.out.println("📥 " + quitResponse);
            }
        } catch (Exception e) {
            System.out.println("ℹ️ Không có response cho lệnh QUIT (bình thường)");
        }
    }
}
