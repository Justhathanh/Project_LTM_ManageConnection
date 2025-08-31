package com.wifiguard.client.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleBooleanProperty;

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
        
        // Auto-connect after a short delay
        javafx.application.Platform.runLater(() -> {
            try {
                Thread.sleep(1000); // Wait 1 second
                onConnect();
            } catch (Exception e) {
                statusLabel.setText("Auto-connect failed: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onConnect() {
        try {
            boolean useTls = tlsToggle.isSelected();
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            
            statusLabel.setText("Connecting to " + host + ":" + port + "...");
            
            closeApi();
            api = new ClientApi(useTls);
            api.connect(host, port);
            statusLabel.setText("Connected " + host + ":" + port + (useTls?" (TLS)":""));
            
            // Auto-refresh data after connection
            onRefresh();
        } catch (Exception e) {
            String errorMsg = "Connect error: " + e.getMessage();
            statusLabel.setText(errorMsg);
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void onRefresh() {
        if (api == null) { 
            statusLabel.setText("Please Connect first"); 
            return; 
        }
        try {
            statusLabel.setText("Requesting device list...");
            String resp = api.send("LIST");
            data.clear();
            
            System.out.println("Server response: " + resp);
            
            if (resp == null || resp.isBlank()) {
                statusLabel.setText("No response from server");
                System.err.println("Empty response from server");
            } else {
                parseServerResponse(resp);
                statusLabel.setText("Loaded " + data.size() + " devices");
                System.out.println("Parsed " + data.size() + " devices");
            }
        } catch (Exception e) {
            String errorMsg = "Refresh error: " + e.getMessage();
            statusLabel.setText(errorMsg);
            System.err.println("Refresh failed: " + e.getMessage());
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
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Try to parse device info from various formats
            if (line.contains("MAC:") && line.contains("IP:")) {
                try {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 5) {
                        String mac = extractValue(parts[1], "MAC:");
                        String ip = extractValue(parts[2], "IP:");
                        String name = extractValue(parts[3], "Hostname:");
                        String status = extractValue(parts[4], "Status:");
                        String last = extractValue(parts[5], "Last Seen:");
                        
                        if (!mac.isEmpty() && !ip.isEmpty()) {
                            boolean known = "KNOWN".equalsIgnoreCase(status.trim());
                            data.add(new Row(ip.trim(), mac.trim(), name.trim(), known, last.trim()));
                            parsedCount++;
                            System.out.println("Parsed device: " + ip + " | " + mac + " | " + name);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing line: " + line + " - " + e.getMessage());
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
                            data.add(new Row(ip, mac, name, false, "Unknown"));
                            parsedCount++;
                            System.out.println("Parsed device (alt format): " + ip + " | " + mac + " | " + name);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing alt format line: " + line + " - " + e.getMessage());
                }
            }
        }
        
        System.out.println("Total devices parsed: " + parsedCount);
        System.out.println("ObservableList size after parsing: " + data.size());
        System.out.println("Table items count: " + table.getItems().size());
        
        // Force refresh the table view
        table.refresh();
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
}
