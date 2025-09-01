package com.wifiguard.server.gateway;

import com.wifiguard.server.model.DeviceInfo;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class WindowsArpGateway implements RouterGateway{
    private static final Pattern ARP_LINE_PATTERN =
    Pattern.compile("^(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9a-fA-F-]{17})\\s+(\\w+)$");

    @Override
    public List<DeviceInfo> ScanDevices() {
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
                        if(ip.startsWith("224.") || ip.startsWith("239.") || 
                        ip.equals("255.255.255.255")) continue;
                        if(mac.equals("FF:FF:FF:FF")) continue;

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

    @Override
    public void refresh() {
        try {
            new ProcessBuilder("arp", "-d", "*").start().waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
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

