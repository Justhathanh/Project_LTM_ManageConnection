package com.wifiguard.server.gateway;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wifiguard.server.model.DeviceInfo;

public class WindowsArpGateway implements RouterGateway {
    private static final Pattern ARP_LINE_PATTERN =
        Pattern.compile("^(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9a-fA-F-]{17})\\s+(\\w+)$");

    @Override
    public Map<String, Device> getConnectedDevices() throws Exception {
        Map<String, Device> devices = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("arp", "-a");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = ARP_LINE_PATTERN.matcher(line.trim());
                    if (m.matches()) {
                        String ip = m.group(1);
                        String mac = normalizeMac(m.group(2));
                        
                        // Bỏ qua broadcast và multicast
                        if (ip.startsWith("224.") || ip.startsWith("239.") || 
                            ip.equals("255.255.255.255")) continue;
                        if (mac.equals("FF:FF:FF:FF:FF:FF")) continue;

                        // Tạo Device object theo interface RouterGateway
                        Device device = new Device(mac, -50, 150); // Default RSSI và TX rate
                        devices.put(mac, device);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new Exception("ARP command failed with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            throw new Exception("Error scanning devices: " + e.getMessage(), e);
        }
        return devices;
    }

    @Override
    public void block(String mac, int banSeconds) throws Exception {
        // Windows ARP gateway không hỗ trợ block trực tiếp
        // Có thể implement bằng cách thêm vào Windows Firewall rules
        throw new UnsupportedOperationException("Blocking not supported in Windows ARP gateway");
    }

    public List<DeviceInfo> scanDevices() {
        List<DeviceInfo> devices = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("arp", "-a");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = ARP_LINE_PATTERN.matcher(line.trim());
                    if (m.matches()) {
                        String ip = m.group(1);
                        String mac = normalizeMac(m.group(2));
                        
                        // Bỏ qua broadcast và multicast
                        if (ip.startsWith("224.") || ip.startsWith("239.") || 
                            ip.equals("255.255.255.255")) continue;
                        if (mac.equals("FF:FF:FF:FF:FF:FF")) continue;

                        DeviceInfo device = DeviceInfo.builder()
                                .ip(ip)
                                .mac(mac)
                                .hostname(resolveHostname(ip))
                                .lastSeen(LocalDateTime.now())
                                .known(false)
                                .buildOrNull();

                        if (device != null) {
                            devices.add(device);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return devices;
    }

    public void refresh() {
        try {
            new ProcessBuilder("arp", "-d", "*").start().waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("arp", "-a").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeMac(String mac) {
        return mac.replace("-", ":").toUpperCase();
    }

    private String resolveHostname(String ip) {
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
            String hostname = addr.getHostName();
            return hostname != null ? hostname : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}

