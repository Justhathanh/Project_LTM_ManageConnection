package com.wifiguard.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;


public class DeviceTableView {

    public static class Row {
        public final String ip; public final String mac; public final String name; public final boolean known; public final String last;
        public Row(String ip, String mac, String name, boolean known, String last){
            this.ip=ip; this.mac=mac; this.name=name; this.known=known; this.last=last;
        }
        public String getIp(){return ip;} public String getMac(){return mac;}
        public String getName(){return name;} public boolean isKnown(){return known;}
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
        colIp.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getIp()));
        colMac.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getMac()));
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        colLast.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getLast()));
        colKnown.setCellValueFactory(c -> new javafx.beans.property.SimpleBooleanProperty(c.getValue().isKnown()));
        colKnown.setCellFactory(CheckBoxTableCell.forTableColumn(colKnown));
        statusLabel.setText("Disconnected");
        hostField.setText("127.0.0.1");
        portField.setText("5000");
    }

    @FXML
    private void onConnect() {
        try {
            boolean useTls = tlsToggle.isSelected();
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            closeApi();
            api = new ClientApi(useTls);
            api.connect(host, port);
            statusLabel.setText("Connected " + host + ":" + port + (useTls?" (TLS)":""));
        } catch (Exception e) {
            statusLabel.setText("Connect error: " + e.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        if (api == null) { statusLabel.setText("Please Connect first"); return; }
        try {
            String resp = api.send("LIST");
            // EXPECT: server trả về dạng đơn giản: mỗi thiết bị 1 dòng "ip mac name known lastSeen"
// Ví dụ demo: mock nếu resp null -> bạn chỉnh lại theo format thật của server
            data.clear();
            if (resp == null || resp.isBlank()) {
                // demo mock (xóa khi nối server thật)
                data.addAll(
                    new Row("192.168.1.10","AA:BB:CC:DD:EE:01","Pixel", false, "now"),
                    new Row("192.168.1.11","AA:BB:CC:DD:EE:02","Laptop", true, "1m ago")
                );
                statusLabel.setText("Loaded (mock)");
            } else {
                // TODO: parse resp → add vào data
                // Tùy bạn chuẩn hóa format Responses ở server
                statusLabel.setText("Loaded");
            }
        } catch (Exception e) {
            statusLabel.setText("Refresh error: " + e.getMessage());
        }
    }

    @FXML
    private void onAdd() {
        Row sel = table.getSelectionModel().getSelectedItem();
        if (api == null) { statusLabel.setText("Please Connect first"); return; }
        if (sel == null) { statusLabel.setText("Select a row to ADD"); return; }
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
        try { if (api != null) api.send("QUIT"); } catch (Exception ignore) {}
        closeApi();
        statusLabel.setText("Disconnected");
    }

    private void closeApi() {
        try { if (api != null) api.close(); } catch (Exception ignore) {}
        api = null;
    }
}