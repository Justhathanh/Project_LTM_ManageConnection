package com.wifiguard.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP Test Client để test WifiGuard HTTP Server
 */
public class HttpTestClient {
    private static final String BASE_URL = "http://127.0.0.1:8080";
    
    public static void main(String[] args) {
        System.out.println("=== HTTP TEST CLIENT ===");
        System.out.println("Testing WifiGuard HTTP Server on " + BASE_URL);
        System.out.println();
        
        try {
            // Test 1: API Status
            testEndpoint("/api/status", "API Status");
            
            // Test 2: Devices API
            testEndpoint("/api/devices", "Devices API");
            
            // Test 3: Allowlist API
            testEndpoint("/api/allowlist", "Allowlist API");
            
            // Test 4: Health Check
            testEndpoint("/health", "Health Check");
            
            // Test 5: Main Page
            testEndpoint("/", "Main Page");
            
            // Test 6: Dashboard
            testEndpoint("/dashboard", "Dashboard");
            
        } catch (Exception e) {
            System.err.println("❌ Error during testing: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n=== TEST COMPLETED ===");
    }
    
    private static void testEndpoint(String endpoint, String description) {
        System.out.println("🔹 Testing: " + description);
        System.out.println("URL: " + BASE_URL + endpoint);
        
        try {
            URL url = new URL(BASE_URL + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                reader.close();
                
                String content = response.toString();
                if (endpoint.startsWith("/api/")) {
                    // JSON response - show first 200 chars
                    System.out.println("Response: " + content.substring(0, Math.min(200, content.length())) + 
                        (content.length() > 200 ? "..." : ""));
                } else {
                    // HTML response - show title
                    if (content.contains("<title>")) {
                        int start = content.indexOf("<title>") + 7;
                        int end = content.indexOf("</title>");
                        if (end > start) {
                            System.out.println("Page Title: " + content.substring(start, end));
                        }
                    }
                    System.out.println("Response Length: " + content.length() + " characters");
                }
            } else {
                System.out.println("❌ Error: " + responseCode);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
        
        System.out.println("---");
    }
}
