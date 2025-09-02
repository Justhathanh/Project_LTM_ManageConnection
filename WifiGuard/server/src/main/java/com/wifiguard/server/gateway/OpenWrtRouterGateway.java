package com.wifiguard.server.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.wifiguard.server.model.DeviceInfo;

/**
 * OpenWrt Router Gateway implementation
 */
public class OpenWrtRouterGateway implements RouterGateway {
    
    private final String host;
    private final String username;
    private final String password;
    private final List<DeviceInfo> knownDevices = new CopyOnWriteArrayList<>();

    public OpenWrtRouterGateway(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    @Override
    public Map<String, Device> getConnectedDevices() throws Exception {
        Map<String, Device> devices = new HashMap<>();
        
        try {
            // TODO: Implement OpenWrt API calls
            // This would typically involve HTTP requests to the router's API endpoints
            // For now, return empty map
            System.out.println("OpenWrt API not fully implemented yet");
            
        } catch (Exception e) {
            throw new Exception("Error getting devices from OpenWrt router: " + e.getMessage(), e);
        }
        
        return devices;
    }

    @Override
    public void block(String mac, int banSeconds) throws Exception {
        try {
            // TODO: Implement OpenWrt blocking via API
            // This would typically involve sending commands to block specific MAC addresses
            System.out.println("Blocking " + mac + " for " + banSeconds + " seconds via OpenWrt API");
            
        } catch (Exception e) {
            throw new Exception("Error blocking device on OpenWrt router: " + e.getMessage(), e);
        }
    }

    /**
     * Quét và phát hiện thiết bị mới.
     * @return danh sách các thiết bị mới xuất hiện kể từ lần quét trước
     */
    public List<DeviceInfo> scanAndDetectNewDevices() {
        List<DeviceInfo> currentDevices = new ArrayList<>();
        List<DeviceInfo> newDevices = new ArrayList<>();
        
        try {
            Map<String, Device> connectedDevices = getConnectedDevices();
            
            // Convert Device objects to DeviceInfo objects
            for (Map.Entry<String, Device> entry : connectedDevices.entrySet()) {
                String mac = entry.getKey();
                Device device = entry.getValue();
                
                DeviceInfo deviceInfo = DeviceInfo.builder()
                        .mac(mac)
                        .hostname("OpenWrt-" + mac.substring(0, 8))
                        .ip("") // IP will be resolved separately
                        .known(false)
                        .buildOrNull();
                
                if (deviceInfo != null) {
                    currentDevices.add(deviceInfo);
                }
            }
            
            // Find new devices
            for (DeviceInfo device : currentDevices) {
                if (!knownDevices.contains(device)) {
                    newDevices.add(device);
                    System.out.println("[NEW DEVICE] " + device.toCompactString());
                }
            }

            // Update known devices list
            knownDevices.clear();
            knownDevices.addAll(currentDevices);
            
        } catch (Exception e) {
            System.err.println("Error scanning OpenWrt devices: " + e.getMessage());
        }

        return newDevices;
    }

    /**
     * Lấy toàn bộ danh sách hiện tại
     */
    public List<DeviceInfo> getCurrentDevices() {
        return new ArrayList<>(knownDevices);
    }
    
    /**
     * Test connection to OpenWrt router
     */
    public boolean testConnection() {
        try {
            // TODO: Implement connection test
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
