package com.wifiguard.client.ui;

import java.io.File;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;

public class DeviceTableView {

    public static class Row {
        public final String ip; 
        public final String mac; 
        public final String name; 
        public final boolean known; 
        public final String last;
        
        public Row(String ip, String mac, String name, boolean known, String last){
            this.ip=ip; this.mac=mac; this.name=name; this.known=known; this.last=last;
        }
        public String getIp(){return ip;} 
        public String getMac(){return mac;}
        public String getName(){return name;} 
        public boolean isKnown(){return known;}
        public String getLast(){return last;}
    }

    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row,String> colIp, colMac, colName, colLast;
    @FXML private TableColumn<Row,Boolean> colKnown;
    @FXML private CheckBox tlsToggle;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private Label statusLabel;

    private final ObservableList<Row> data = FXCollections.observableArrayList();
    private ClientApi api;
    
    // Server management
    private Process serverProcess;
    private boolean serverStarted = false;
    private String serverJarPath;

    @FXML
    public void initialize() {
        table.setItems(data);
        colIp.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIp()));
        colMac.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMac()));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colLast.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLast()));
        colKnown.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isKnown()));
        colKnown.setCellFactory(CheckBoxTableCell.forTableColumn(colKnown));
        statusLabel.setText("Ready to connect");
        hostField.setText("127.0.0.1"); // Localhost
        portField.setText("9099");
        
        // Ensure TLS is unchecked by default (server doesn't support SSL)
        tlsToggle.setSelected(false);
        
        // Auto-start server and connect after a short delay
        javafx.application.Platform.runLater(() -> {
            try {
                Thread.sleep(1000); // Wait 1 second for UI to fully load
                System.out.println("Auto-starting server...");
                startServer();
            } catch (Exception e) {
                System.err.println("Auto-start server failed: " + e.getMessage());
                statusLabel.setText("Auto-start server failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Start the server process
     */
    private void startServer() {
        // Run server startup in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                javafx.application.Platform.runLater(() -> 
                    statusLabel.setText("Starting server..."));
                
                // Kill any existing server processes first
                killExistingServerProcesses();
                
                // Wait a bit for ports to be freed
                Thread.sleep(2000);
                
                // Find server JAR file
                File serverDir = new File("../server");
                File serverJar = new File(serverDir, "target/wifiguard-server-1.0.0-jar-with-dependencies.jar");
                
                if (!serverJar.exists()) {
                    javafx.application.Platform.runLater(() -> {
                        statusLabel.setText("Server JAR not found. Please build server first.");
                        System.err.println("Server JAR not found at: " + serverJar.getAbsolutePath());
                    });
                    return;
                }
                
                serverJarPath = serverJar.getAbsolutePath();
                System.out.println("Starting server from: " + serverJarPath);
                
                // Start server process with retry logic
                final int maxRetries = 3;
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    final int currentAttempt = attempt;
                    try {
                        System.out.println("Attempt " + currentAttempt + " to start server...");
                        
                        // Start server process
                        ProcessBuilder pb = new ProcessBuilder("java", "-jar", serverJarPath);
                        pb.directory(serverDir);
                        pb.inheritIO(); // Show server output in console
                        
                        serverProcess = pb.start();
                        serverStarted = true;
                        
                        javafx.application.Platform.runLater(() -> 
                            statusLabel.setText("Server starting... (Attempt " + currentAttempt + ")"));
                        System.out.println("Server process started with PID: " + getProcessId(serverProcess));
                        
                        // Wait for server to be ready, then connect
                        Thread.sleep(8000); // Wait 8 seconds for server to fully start and scan devices
                        System.out.println("Server should be ready, attempting to connect...");
                        
                        javafx.application.Platform.runLater(() -> onConnect());
                        
                        // If we get here, server started successfully
                        break;
                        
                    } catch (Exception e) {
                        System.err.println("Attempt " + currentAttempt + " failed: " + e.getMessage());
                        if (currentAttempt < maxRetries) {
                            javafx.application.Platform.runLater(() -> 
                                statusLabel.setText("Server start failed, retrying... (Attempt " + currentAttempt + "/" + maxRetries + ")"));
                            Thread.sleep(3000); // Wait before retry
                            killExistingServerProcesses(); // Kill any partial processes
                        } else {
                            throw e; // Re-throw on final attempt
                        }
                    }
                }
                
            } catch (Exception e) {
                String errorMsg = "Failed to start server after all attempts: " + e.getMessage();
                javafx.application.Platform.runLater(() -> statusLabel.setText(errorMsg));
                System.err.println(errorMsg);
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Get process ID (Windows-specific)
     */
    private long getProcessId(Process process) {
        try {
            // For Windows, we can't easily get PID from Process object
            // This is a workaround
            return process.pid();
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * Schedule auto-refresh with delay
     */
    private void scheduleAutoRefresh(int delayMs) {
        // Run auto-refresh in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                System.out.println("Auto-refresh attempt after " + delayMs + "ms delay");
                
                javafx.application.Platform.runLater(() -> {
                    if (api != null && isServerRunning()) {
                        onRefresh();
                    } else {
                        System.out.println("Skipping auto-refresh: API not ready or server not running");
                    }
                });
            } catch (Exception e) {
                System.err.println("Auto-refresh failed after " + delayMs + "ms delay: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Start continuous device monitoring to catch new devices
     */
    private void startDeviceMonitoring() {
        new Thread(() -> {
            while (serverStarted && isServerRunning() && api != null) {
                try {
                    Thread.sleep(15000); // Check for new devices every 15 seconds
                    
                    System.out.println("Checking for new devices...");
                    javafx.application.Platform.runLater(() -> 
                        statusLabel.setText("Checking for new devices..."));
                    
                    // Get current device count
                    int currentCount = data.size();
                    
                    // Try to refresh device list
                    try {
                        String resp = api.send("LIST");
                        if (resp != null && !resp.isBlank() && hasDeviceData(resp)) {
                            // Parse response to get new count
                            int newCount = countDevicesInResponse(resp);
                            
                            if (newCount > currentCount) {
                                System.out.println("New devices detected! Old: " + currentCount + ", New: " + newCount);
                                javafx.application.Platform.runLater(() -> {
                                    statusLabel.setText("New devices detected! Updating list...");
                                    // Parse and add new devices without clearing existing ones
                                    parseServerResponseAdditive(resp);
                                });
                            } else {
                                System.out.println("No new devices. Current count: " + currentCount);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Device monitoring refresh failed: " + e.getMessage());
                        if (isConnectionError(e)) {
                            System.out.println("Connection error in device monitoring, stopping...");
                            break;
                        }
                    }
                    
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Device monitoring error: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * Count devices in server response without parsing them
     */
    private int countDevicesInResponse(String response) {
        if (response == null || response.isBlank()) return 0;
        
        String[] lines = response.split("\n");
        int count = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Count lines that look like device data
            if ((line.contains("MAC:") && line.contains("IP:")) || 
                (line.contains("|") && line.split("\\|").length >= 3)) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Parse server response and add new devices without clearing existing ones
     */
    private void parseServerResponseAdditive(String response) {
        System.out.println("=== PARSING SERVER RESPONSE (ADDITIVE) ===");
        System.out.println("Response length: " + response.length());
        System.out.println("Current device count: " + data.size());
        
        String[] lines = response.split("\n");
        int parsedCount = 0;
        int lineCount = 0;
        
        System.out.println("Total lines to parse: " + lines.length);
        
        for (String line : lines) {
            lineCount++;
            line = line.trim();
            if (line.isEmpty()) continue;
            
            System.out.println("Parsing line " + lineCount + ": " + line);
            
            // Try to parse device info from various formats
            if (line.contains("MAC:") && line.contains("IP:")) {
                try {
                    String[] parts = line.split("\\|");
                    System.out.println("Line has " + parts.length + " parts");
                    
                    if (parts.length >= 5) {
                        String mac = extractValue(parts[1], "MAC:");
                        String ip = extractValue(parts[2], "IP:");
                        String name = extractValue(parts[3], "Hostname:");
                        String status = extractValue(parts[4], "Status:");
                        String last = extractValue(parts[5], "Last Seen:");
                        
                        System.out.println("Extracted values - MAC: '" + mac + "', IP: '" + ip + "', Name: '" + name + "'");
                        
                        if (!mac.isEmpty() && !ip.isEmpty()) {
                            // Check if device already exists
                            boolean deviceExists = false;
                            for (Row existingRow : data) {
                                if (existingRow.getMac().equals(mac.trim()) || existingRow.getIp().equals(ip.trim())) {
                                    deviceExists = true;
                                    break;
                                }
                            }
                            
                            if (!deviceExists) {
                                boolean known = "KNOWN".equalsIgnoreCase(status.trim());
                                Row newRow = new Row(ip.trim(), mac.trim(), name.trim(), known, last.trim());
                                data.add(newRow);
                                parsedCount++;
                                System.out.println("Added new device: " + ip + " | " + mac + " | " + name);
                            } else {
                                System.out.println("Device already exists: " + ip + " | " + mac + " | " + name);
                            }
                        } else {
                            System.out.println("Skipping line - empty MAC or IP");
                        }
                    } else {
                        System.out.println("Skipping line - insufficient parts: " + parts.length);
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing line: " + line + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
            // Try to parse from other formats
            else if (line.contains("|") && line.split("\\|").length >= 3) {
                try {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 3) {
                        String ip = parts[0].trim();
                        String mac = parts[1].trim();
                        String name = parts[2].trim();
                        
                        if (!ip.isEmpty() && !mac.isEmpty() && !ip.equals("IP") && !mac.equals("MAC")) {
                            // Check if device already exists
                            boolean deviceExists = false;
                            for (Row existingRow : data) {
                                if (existingRow.getMac().equals(mac) || existingRow.getIp().equals(ip)) {
                                    deviceExists = true;
                                    break;
                                }
                            }
                            
                            if (!deviceExists) {
                                Row newRow = new Row(ip, mac, name, false, "Unknown");
                                data.add(newRow);
                                parsedCount++;
                                System.out.println("Added new device (alt format): " + ip + " | " + mac + " | " + name);
                            } else {
                                System.out.println("Device already exists (alt format): " + ip + " | " + mac + " | " + name);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing alt format line: " + line + " - " + e.getMessage());
                }
            } else {
                System.out.println("Line format not recognized: " + line);
            }
        }
        
        System.out.println("=== PARSING SUMMARY (ADDITIVE) ===");
        System.out.println("Total lines processed: " + lineCount);
        System.out.println("New devices added: " + parsedCount);
        System.out.println("Total devices after parsing: " + data.size());
        System.out.println("Table items count: " + table.getItems().size());
        System.out.println("========================");
        
        // Force refresh the table view
        table.refresh();
        System.out.println("Table view refreshed");
    }
    
    /**
     * Start connection monitoring to detect and handle disconnections
     */
    private void startConnectionMonitoring() {
        new Thread(() -> {
            while (serverStarted && isServerRunning()) {
                try {
                    Thread.sleep(10000); // Check every 10 seconds
                    
                    // Test connection with a simple PING command
                    if (api != null) {
                        try {
                            String response = api.send("PING");
                            if (response == null || response.isBlank()) {
                                System.out.println("Connection test failed, attempting to reconnect...");
                                javafx.application.Platform.runLater(() -> {
                                    statusLabel.setText("Connection lost, reconnecting...");
                                    reconnectToServer();
                                });
                            } else {
                                System.out.println("Connection test successful: " + response);
                            }
                        } catch (Exception e) {
                            System.out.println("Connection test exception: " + e.getMessage());
                            if (isConnectionError(e)) {
                                javafx.application.Platform.runLater(() -> {
                                    statusLabel.setText("Connection error detected, reconnecting...");
                                    reconnectToServer();
                                });
                            }
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Connection monitoring error: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * Reconnect to server when connection is lost
     */
    private void reconnectToServer() {
        try {
            System.out.println("Attempting to reconnect to server...");
            
            // Close existing connection
            closeApi();
            
            // Wait a bit before reconnecting
            Thread.sleep(2000);
            
            // Try to reconnect
            if (isServerRunning()) {
                onConnect();
            } else {
                System.out.println("Server not running, restarting...");
                startServer();
            }
        } catch (Exception e) {
            System.err.println("Reconnection failed: " + e.getMessage());
            statusLabel.setText("Reconnection failed: " + e.getMessage());
        }
    }

    @FXML
    private void onConnect() {
        try {
            // Check if server is running
            if (!isServerRunning()) {
                statusLabel.setText("Server not running. Starting server...");
                startServer();
                return;
            }
            
            boolean useTls = tlsToggle.isSelected();
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            
            statusLabel.setText("Connecting to " + host + ":" + port + "...");
            
            closeApi();
            api = new ClientApi(useTls);
            api.connect(host, port);
            statusLabel.setText("Connected " + host + ":" + port + (useTls?" (TLS)":""));
            
            // Auto-refresh data after connection with delay to ensure connection is stable
            System.out.println("Connection successful, refreshing device list...");
            statusLabel.setText("Connection established, refreshing device list...");
            
            // Use retry logic for more reliable refresh with longer delays
            scheduleAutoRefresh(2000);  // First try after 2 seconds
            scheduleAutoRefresh(5000);  // Second try after 5 seconds  
            scheduleAutoRefresh(10000); // Third try after 10 seconds
            
            // Start continuous device monitoring to catch new devices
            startDeviceMonitoring();
            
            // Start connection monitoring to detect disconnections
            startConnectionMonitoring();
            
        } catch (Exception e) {
            String errorMsg = "Connect error: " + e.getMessage();
            statusLabel.setText(errorMsg);
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
            
            // If connection fails, try to restart server and reconnect
            if (isConnectionError(e)) {
                statusLabel.setText("Connection failed, attempting to restart server...");
                System.out.println("Connection error detected, attempting to restart server...");
                startServer();
            }
        }
    }

    @FXML
    private void onRefresh() {
        // Check if server is running before refreshing
        if (!isServerRunning()) {
            statusLabel.setText("Server not running. Please start server first.");
            System.out.println("Refresh skipped: Server not running");
            return;
        }
        
        if (api == null) { 
            statusLabel.setText("Please Connect first"); 
            System.out.println("Refresh skipped: API not ready");
            return; 
        }
        
        // Try to refresh with retry logic
        refreshWithRetry(3);
    }
    
    /**
     * Refresh with retry logic to handle connection errors
     */
    private void refreshWithRetry(int maxRetries) {
        new Thread(() -> {
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                final int currentAttempt = attempt;
                try {
                    System.out.println("Refresh attempt " + currentAttempt + "/" + maxRetries);
                    
                    javafx.application.Platform.runLater(() -> 
                        statusLabel.setText("Requesting device list... (Attempt " + currentAttempt + "/" + maxRetries + ")"));
                    
                    // Check if connection is still valid
                    if (!isConnectionValid()) {
                        System.out.println("Connection lost, attempting to reconnect...");
                        javafx.application.Platform.runLater(() -> reconnectToServer());
                        try {
                            Thread.sleep(3000); // Wait longer before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }
                    
                    String resp = api.send("LIST");
                    
                    System.out.println("Server response received, length: " + (resp != null ? resp.length() : "null"));
                    System.out.println("Server response: " + resp);
                    
                    if (resp == null || resp.isBlank()) {
                        System.out.println("Empty response from server, attempt " + currentAttempt);
                        if (currentAttempt < maxRetries) {
                            try {
                                Thread.sleep(1000); // Wait before retry
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            continue;
                        }
                        javafx.application.Platform.runLater(() -> 
                            statusLabel.setText("No response from server after " + maxRetries + " attempts"));
                        return;
                    }
                    
                    // Check if response contains device data
                    if (!hasDeviceData(resp)) {
                        System.out.println("Response does not contain device data, attempt " + currentAttempt);
                        if (currentAttempt < maxRetries) {
                            try {
                                Thread.sleep(1000); // Wait before retry
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            continue;
                        }
                        javafx.application.Platform.runLater(() -> 
                            statusLabel.setText("Response does not contain device data after " + maxRetries + " attempts"));
                        return;
                    }
                    
                    // Success! Parse the response
                    javafx.application.Platform.runLater(() -> {
                        try {
                            // Clear existing data before parsing (only for manual refresh)
                            data.clear();
                            System.out.println("Cleared existing data, starting to parse response...");
                            
                            parseServerResponse(resp);
                            
                            if (data.size() > 0) {
                                statusLabel.setText("Loaded " + data.size() + " devices successfully");
                                System.out.println("Successfully parsed " + data.size() + " devices");
                            } else {
                                statusLabel.setText("No devices found in response");
                                System.out.println("No devices parsed from response");
                            }
                        } catch (Exception e) {
                            System.err.println("Error parsing response: " + e.getMessage());
                            statusLabel.setText("Error parsing response: " + e.getMessage());
                        }
                    });
                    
                    // Success, exit retry loop
                    return;
                    
                } catch (Exception e) {
                    System.err.println("Refresh attempt " + currentAttempt + " failed: " + e.getMessage());
                    
                    if (currentAttempt < maxRetries) {
                        javafx.application.Platform.runLater(() -> 
                            statusLabel.setText("Refresh failed, retrying... (Attempt " + currentAttempt + "/" + maxRetries + ")"));
                        
                        // If it's a connection error, try to reconnect
                        if (isConnectionError(e)) {
                            System.out.println("Connection error detected, attempting to reconnect...");
                            javafx.application.Platform.runLater(() -> onConnect());
                            try {
                                Thread.sleep(3000); // Wait longer before retry
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        } else {
                            try {
                                Thread.sleep(1000); // Normal retry delay
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    } else {
                        // Final attempt failed
                        String errorMsg = "Refresh failed after " + maxRetries + " attempts: " + e.getMessage();
                        javafx.application.Platform.runLater(() -> statusLabel.setText(errorMsg));
                        System.err.println(errorMsg);
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @FXML
    private void onRestartServer() {
        try {
            statusLabel.setText("Restarting server...");
            System.out.println("Restarting server...");
            
            // Close existing connection
            closeApi();
            
            // Run restart in background thread to avoid blocking UI
            new Thread(() -> {
                try {
                    // Kill existing server processes and start new one
                    startServer();
                } catch (Exception e) {
                    String errorMsg = "Restart server error: " + e.getMessage();
                    javafx.application.Platform.runLater(() -> statusLabel.setText(errorMsg));
                    System.err.println(errorMsg);
                    e.printStackTrace();
                }
            }).start();
            
        } catch (Exception e) {
            String errorMsg = "Restart server error: " + e.getMessage();
            statusLabel.setText(errorMsg);
            System.err.println(errorMsg);
            e.printStackTrace();
        }
    }

    @FXML
    private void onAdd() {
        Row sel = table.getSelectionModel().getSelectedItem();
        if (api == null) { 
            statusLabel.setText("Please Connect first"); 
            return; 
        }
        if (sel == null) { 
            statusLabel.setText("Select a row to ADD"); 
            return; 
        }
        try {
            String resp = api.send("ADD " + sel.mac);
            statusLabel.setText(resp != null ? resp : "Added");
            onRefresh();
        } catch (Exception e) {
            statusLabel.setText("Add error: " + e.getMessage());
        }
    }

    @FXML
    private void onQuit() {
        try {
            if (api != null) {
                api.send("QUIT");
                closeApi();
            }
            statusLabel.setText("Disconnected");
            
            // Stop server if we started it
            if (serverStarted && serverProcess != null) {
                statusLabel.setText("Stopping server...");
                System.out.println("Stopping server process...");
                
                // Send graceful shutdown signal
                try {
                    serverProcess.destroy();
                    
                    // Wait for graceful shutdown
                    if (!serverProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        // Force kill if not responding
                        serverProcess.destroyForcibly();
                        System.out.println("Server force killed");
                    } else {
                        System.out.println("Server stopped gracefully");
                    }
                } catch (Exception e) {
                    System.err.println("Error stopping server: " + e.getMessage());
                    serverProcess.destroyForcibly();
                }
                
                serverStarted = false;
                serverProcess = null;
                statusLabel.setText("Server stopped");
            }
            
            // Exit application
            javafx.application.Platform.exit();
            
        } catch (Exception e) {
            statusLabel.setText("Quit error: " + e.getMessage());
        }
    }

    private void parseServerResponse(String response) {
        System.out.println("=== PARSING SERVER RESPONSE ===");
        System.out.println("Response length: " + response.length());
        System.out.println("Response content:");
        System.out.println(response);
        System.out.println("=== END RESPONSE ===");
        
        String[] lines = response.split("\n");
        int parsedCount = 0;
        int lineCount = 0;
        
        System.out.println("Total lines to parse: " + lines.length);
        
        for (String line : lines) {
            lineCount++;
            line = line.trim();
            if (line.isEmpty()) continue;
            
            System.out.println("Parsing line " + lineCount + ": " + line);
            
            // Try to parse device info from various formats
            if (line.contains("MAC:") && line.contains("IP:")) {
                try {
                    String[] parts = line.split("\\|");
                    System.out.println("Line has " + parts.length + " parts");
                    
                    if (parts.length >= 5) {
                        String mac = extractValue(parts[1], "MAC:");
                        String ip = extractValue(parts[2], "IP:");
                        String name = extractValue(parts[3], "Hostname:");
                        String status = extractValue(parts[4], "Status:");
                        String last = extractValue(parts[5], "Last Seen:");
                        
                        System.out.println("Extracted values - MAC: '" + mac + "', IP: '" + ip + "', Name: '" + name + "'");
                        
                        if (!mac.isEmpty() && !ip.isEmpty()) {
                            boolean known = "KNOWN".equalsIgnoreCase(status.trim());
                            Row newRow = new Row(ip.trim(), mac.trim(), name.trim(), known, last.trim());
                            data.add(newRow);
                            parsedCount++;
                            System.out.println("Successfully parsed device: " + ip + " | " + mac + " | " + name);
                        } else {
                            System.out.println("Skipping line - empty MAC or IP");
                        }
                    } else {
                        System.out.println("Skipping line - insufficient parts: " + parts.length);
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing line: " + line + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
            // Try to parse from other formats
            else if (line.contains("|") && line.split("\\|").length >= 3) {
                try {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 3) {
                        String ip = parts[0].trim();
                        String mac = parts[1].trim();
                        String name = parts[2].trim();
                        
                        if (!ip.isEmpty() && !mac.isEmpty() && !ip.equals("IP") && !mac.equals("MAC")) {
                            Row newRow = new Row(ip, mac, name, false, "Unknown");
                            data.add(newRow);
                            parsedCount++;
                            System.out.println("Successfully parsed device (alt format): " + ip + " | " + mac + " | " + name);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing alt format line: " + line + " - " + e.getMessage());
                }
            } else {
                System.out.println("Line format not recognized: " + line);
            }
        }
        
        System.out.println("=== PARSING SUMMARY ===");
        System.out.println("Total lines processed: " + lineCount);
        System.out.println("Total devices parsed: " + parsedCount);
        System.out.println("ObservableList size after parsing: " + data.size());
        System.out.println("Table items count: " + table.getItems().size());
        System.out.println("========================");
        
        // Force refresh the table view
        table.refresh();
        System.out.println("Table view refreshed");
    }

    private String extractValue(String part, String prefix) {
        if (part.contains(prefix)) {
            return part.substring(part.indexOf(prefix) + prefix.length()).trim();
        }
        return "";
    }

    private void closeApi() {
        try {
            if (api != null) {
                api.close();
                api = null;
            }
        } catch (Exception ignore) {}
    }
    
    /**
     * Check if server is running
     */
    private boolean isServerRunning() {
        if (serverProcess == null) return false;
        
        try {
            // Check if process is still alive
            int exitCode = serverProcess.exitValue();
            // If we can get exit code, process has terminated
            System.out.println("Server process terminated with exit code: " + exitCode);
            serverStarted = false;
            return false;
        } catch (IllegalThreadStateException e) {
            // Process is still running
            return true;
        }
    }
    
    /**
     * Check if response contains device data
     */
    private boolean hasDeviceData(String response) {
        if (response == null || response.isBlank()) return false;
        
        // Check for common device indicators
        return response.contains("MAC:") || 
               response.contains("IP:") || 
               response.contains("Devices:") ||
               response.contains("Device List");
    }
    
    /**
     * Check if connection is still valid
     */
    private boolean isConnectionValid() {
        return api != null && isServerRunning();
    }
    
    /**
     * Check if exception is a connection-related error
     */
    private boolean isConnectionError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        
        return message.contains("Connection refused") ||
               message.contains("Connection reset") ||
               message.contains("Connection aborted") ||
               message.contains("SocketException") ||
               message.contains("IOException") ||
               message.contains("EOFException") ||
               message.contains("Broken pipe") ||
               message.contains("Network is unreachable") ||
               message.contains("No route to host");
    }
    
    /**
     * Kill any existing server processes that might be using port 9099
     */
    private void killExistingServerProcesses() {
        try {
            System.out.println("Killing existing server processes...");
            
            // Kill our own server process if it exists
            if (serverProcess != null) {
                try {
                    serverProcess.destroy();
                    if (!serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                        serverProcess.destroyForcibly();
                    }
                    System.out.println("Killed existing server process");
                } catch (Exception e) {
                    System.err.println("Error killing existing server process: " + e.getMessage());
                }
                serverProcess = null;
                serverStarted = false;
            }
            
            // Try to kill any Java processes using port 9099 (Windows-specific)
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", 
                    "for /f \"tokens=5\" %a in ('netstat -ano ^| findstr :9099') do taskkill /F /PID %a");
                pb.start();
                System.out.println("Attempted to kill processes using port 9099");
            } catch (Exception e) {
                System.err.println("Could not kill processes using port 9099: " + e.getMessage());
            }
            
            // Wait a bit for processes to be killed
            Thread.sleep(1000);
            
        } catch (Exception e) {
            System.err.println("Error in killExistingServerProcesses: " + e.getMessage());
        }
    }
}
