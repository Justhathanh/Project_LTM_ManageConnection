package com.wifiguard.server;

public class TlsTest {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== Testing TLS Configuration ===");
            
            // Test cấu hình TLS
            ServerConfig config = new ServerConfig();
            SecurityConfig security = new SecurityConfig(config);
            
            // Kiểm tra xem TLS có được bật không
            boolean tlsEnabled = security.tlsEnabled;
            System.out.println("TLS Enabled: " + tlsEnabled);
            
            if (tlsEnabled) {
                // Test tạo SSL context
                var sslContext = security.buildSSLContext();
                if (sslContext != null) {
                    System.out.println("SSL Context created successfully");
                } else {
                    System.out.println("SSL Context creation failed");
                }
            }
            
            System.out.println("=== TLS Test completed ===");
            
        } catch (Exception e) {
            System.err.println("TLS test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
