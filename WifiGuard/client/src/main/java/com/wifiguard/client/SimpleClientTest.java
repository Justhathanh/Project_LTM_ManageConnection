package com.wifiguard.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SimpleClientTest {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 9099;
    private static final int TIMEOUT_MS = 15000;

    public static void main(String[] args) {
        System.out.println("=== WifiGuard Simple Client Test ===");
        System.out.println("Connecting to server: " + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println();

        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(TIMEOUT_MS);
            
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("✅ Connected to server successfully!");
            System.out.println();

            // Test LIST command to get all devices
            System.out.println("📋 Requesting device list...");
            out.println("LIST");
            out.flush();

            System.out.println("📡 Server response:");
            System.out.println("----------------------------------------");
            
            String line;
            int lineCount = 0;
            while ((line = in.readLine()) != null && lineCount < 50) {
                System.out.println(line);
                lineCount++;
                
                // Stop if we see the end of response
                if (line.contains("+--------------------------------------------------") || 
                    line.contains("End of response") ||
                    line.isEmpty()) {
                    break;
                }
            }
            System.out.println("----------------------------------------");
            System.out.println();

            // Test STATUS command
            System.out.println("📊 Requesting server status...");
            out.println("STATUS");
            out.flush();

            System.out.println("📡 Server status:");
            System.out.println("----------------------------------------");
            lineCount = 0;
            while ((line = in.readLine()) != null && lineCount < 30) {
                System.out.println(line);
                lineCount++;
                
                if (line.contains("+--------------------------------------------------") || 
                    line.contains("End of response") ||
                    line.isEmpty()) {
                    break;
                }
            }
            System.out.println("----------------------------------------");
            System.out.println();

            // Test QUIT command
            System.out.println("👋 Sending QUIT command...");
            out.println("QUIT");
            out.flush();

            System.out.println("✅ Client test completed successfully!");
            
        } catch (Exception e) {
            System.err.println("❌ Error connecting to server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
