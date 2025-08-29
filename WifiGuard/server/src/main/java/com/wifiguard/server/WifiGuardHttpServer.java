package com.wifiguard.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.wifiguard.server.model.DeviceInfo;
import com.wifiguard.server.protocol.Response;

/**
 * HTTP Server adapter cho WifiGuard
 * Hỗ trợ REST API và web interface
 */
public class WifiGuardHttpServer {
    private static final Logger logger = Logger.getLogger(WifiGuardHttpServer.class.getName());
    private static final int HTTP_PORT = 8080;
    private static final int THREAD_POOL_SIZE = 10;
    
    private final HttpServer server;
    private final DeviceMonitor deviceMonitor;
    private final Allowlist allowlist;
    
    public WifiGuardHttpServer(DeviceMonitor deviceMonitor, Allowlist allowlist) throws IOException {
        this.deviceMonitor = deviceMonitor;
        this.allowlist = allowlist;
        
        // Tạo HTTP server
        this.server = HttpServer.create(
            new InetSocketAddress(HTTP_PORT), 0);
        
        // Cấu hình thread pool
        this.server.setExecutor(Executors.newFixedThreadPool(THREAD_POOL_SIZE));
        
        // Đăng ký các endpoints
        setupEndpoints();
        
        logger.info("HTTP Server initialized on port " + HTTP_PORT);
    }
    
    /**
     * Thiết lập các endpoints
     */
    private void setupEndpoints() {
        // REST API endpoints
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/devices", new DevicesHandler());
        server.createContext("/api/allowlist", new AllowlistHandler());
        
        // Web interface endpoints
        server.createContext("/", new WebInterfaceHandler());
        server.createContext("/dashboard", new DashboardHandler());
        
        // Health check
        server.createContext("/health", new HealthHandler());
    }
    
    /**
     * Khởi động HTTP server
     */
    public void start() {
        server.start();
        logger.info("HTTP Server started on port " + HTTP_PORT);
    }
    
    /**
     * Dừng HTTP server
     */
    public void stop() {
        server.stop(0);
        logger.info("HTTP Server stopped");
    }
    
    /**
     * Handler cho /api/status
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    String status = "WifiGuard Server is running";
                    String response = createJsonResponse("status", status);
                    
                    sendResponse(exchange, 200, response, "application/json");
                } else {
                    sendResponse(exchange, 405, "Method not allowed", "text/plain");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling status request", e);
                sendResponse(exchange, 500, "Internal server error", "text/plain");
            }
        }
    }
    
    /**
     * Handler cho /api/devices
     */
    private class DevicesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    List<DeviceInfo> devices = deviceMonitor.getAllDevices();
                    String response = createDevicesJson(devices);
                    
                    sendResponse(exchange, 200, response, "application/json");
                } else {
                    sendResponse(exchange, 405, "Method not allowed", "text/plain");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling devices request", e);
                sendResponse(exchange, 500, "Internal server error", "text/plain");
            }
        }
    }
    
    /**
     * Handler cho /api/allowlist
     */
    private class AllowlistHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    List<DeviceInfo> allowedDevices = allowlist.getAllDevices();
                    String response = createAllowlistJson(allowedDevices);
                    
                    sendResponse(exchange, 200, response, "application/json");
                } else {
                    sendResponse(exchange, 405, "Method not allowed", "text/plain");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling allowlist request", e);
                sendResponse(exchange, 500, "Internal server error", "text/plain");
            }
        }
    }
    
    /**
     * Handler cho web interface chính
     */
    private class WebInterfaceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    String html = createMainPage();
                    sendResponse(exchange, 200, html, "text/html");
                } else {
                    sendResponse(exchange, 405, "Method not allowed", "text/plain");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling web interface request", e);
                sendResponse(exchange, 500, "Internal server error", "text/plain");
            }
        }
    }
    
    /**
     * Handler cho dashboard
     */
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    String html = createDashboardPage();
                    sendResponse(exchange, 200, html, "text/html");
                } else {
                    sendResponse(exchange, 405, "Method not allowed", "text/plain");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling dashboard request", e);
                sendResponse(exchange, 500, "Internal server error", "text/plain");
            }
        }
    }
    
    /**
     * Handler cho health check
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    String response = "OK";
                    sendResponse(exchange, 200, response, "text/plain");
                } else {
                    sendResponse(exchange, 405, "Method not allowed", "text/plain");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling health check", e);
                sendResponse(exchange, 500, "Internal server error", "text/plain");
            }
        }
    }
    
    /**
     * Gửi HTTP response
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        
        byte[] responseBytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * Tạo JSON response đơn giản
     */
    private String createJsonResponse(String key, String value) {
        return "{\"" + key + "\":\"" + value + "\"}";
    }
    
    /**
     * Tạo JSON cho danh sách thiết bị
     */
    private String createDevicesJson(List<DeviceInfo> devices) {
        StringBuilder json = new StringBuilder();
        json.append("{\"devices\":[");
        
        for (int i = 0; i < devices.size(); i++) {
            DeviceInfo device = devices.get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"mac\":\"").append(device.getMac()).append("\",");
            json.append("\"ip\":\"").append(device.getIp()).append("\",");
            json.append("\"hostname\":\"").append(device.getHostname()).append("\",");
            json.append("\"known\":").append(device.isKnown()).append(",");
            json.append("\"lastSeen\":\"").append(device.getLastSeen()).append("\"");
            json.append("}");
        }
        
        json.append("],\"count\":").append(devices.size()).append("}");
        return json.toString();
    }
    
    /**
     * Tạo JSON cho allowlist
     */
    private String createAllowlistJson(List<DeviceInfo> devices) {
        StringBuilder json = new StringBuilder();
        json.append("{\"allowlist\":[");
        
        for (int i = 0; i < devices.size(); i++) {
            DeviceInfo device = devices.get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"mac\":\"").append(device.getMac()).append("\",");
            json.append("\"ip\":\"").append(device.getIp()).append("\",");
            json.append("\"hostname\":\"").append(device.getHostname()).append("\",");
            json.append("\"lastSeen\":\"").append(device.getLastSeen()).append("\"");
            json.append("}");
        }
        
        json.append("],\"count\":").append(devices.size()).append("}");
        return json.toString();
    }
    
    /**
     * Tạo trang web chính
     */
    private String createMainPage() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>WifiGuard Server</title>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    h1 { color: #333; text-align: center; }
                    .nav { text-align: center; margin: 20px 0; }
                    .nav a { display: inline-block; margin: 10px; padding: 10px 20px; background: #007bff; color: white; text-decoration: none; border-radius: 5px; }
                    .nav a:hover { background: #0056b3; }
                    .status { background: #d4edda; border: 1px solid #c3e6cb; color: #155724; padding: 15px; border-radius: 5px; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🚀 WifiGuard Server</h1>
                    <div class="status">
                        <strong>Status:</strong> Server đang chạy trên port 8080
                    </div>
                    <div class="nav">
                        <a href="/dashboard">📊 Dashboard</a>
                        <a href="/api/status">🔍 API Status</a>
                        <a href="/api/devices">📱 Devices</a>
                        <a href="/api/allowlist">✅ Allowlist</a>
                        <a href="/health">💚 Health Check</a>
                    </div>
                    <p style="text-align: center; color: #666;">
                        TCP Socket Server: Port 9099 | HTTP Server: Port 8080
                    </p>
                </div>
            </body>
            </html>
            """;
    }
    
    /**
     * Tạo trang dashboard
     */
    private String createDashboardPage() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>WifiGuard Dashboard</title>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    h1 { color: #333; text-align: center; }
                    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }
                    .stat-card { background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center; border-left: 4px solid #007bff; }
                    .stat-number { font-size: 2em; font-weight: bold; color: #007bff; }
                    .stat-label { color: #666; margin-top: 10px; }
                    .back-link { text-align: center; margin: 20px 0; }
                    .back-link a { color: #007bff; text-decoration: none; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>📊 WifiGuard Dashboard</h1>
                    <div class="stats">
                        <div class="stat-card">
                            <div class="stat-number" id="deviceCount">-</div>
                            <div class="stat-label">Thiết bị trên mạng</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-number" id="allowlistCount">-</div>
                            <div class="stat-label">Thiết bị được phép</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-number" id="serverStatus">🟢</div>
                            <div class="stat-label">Trạng thái Server</div>
                        </div>
                    </div>
                    <div class="back-link">
                        <a href="/">← Quay lại trang chủ</a>
                    </div>
                </div>
                <script>
                    // Load data
                    fetch('/api/devices')
                        .then(response => response.json())
                        .then(data => {
                            document.getElementById('deviceCount').textContent = data.count;
                        });
                    
                    fetch('/api/allowlist')
                        .then(response => response.json())
                        .then(data => {
                            document.getElementById('allowlistCount').textContent = data.count;
                        });
                </script>
            </body>
            </html>
            """;
    }
}
