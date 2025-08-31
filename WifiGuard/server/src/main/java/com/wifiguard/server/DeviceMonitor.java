package com.wifiguard.server;

import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.wifiguard.server.model.DeviceInfo;

/**
 * Monitors network devices and polls from Gateway
 */
public class DeviceMonitor {
    private static final Logger logger = Logger.getLogger(DeviceMonitor.class.getName());
    private static final String OPENWRT_MODE = "openwrt";
    private static final String DUMMY_MODE = "dummy";
    
    private final Allowlist allowlist;
    private final Map<String, DeviceInfo> discoveredDevices;
    private final ScheduledExecutorService scheduler;
    private final int pollIntervalSeconds;
    private final int banSeconds;
    private final int networkScanRange;
    private final int pingTimeout;
    private final String routerMode;
    private final String openwrtHost;
    private final String openwrtUsername;
    private final String openwrtPassword;
    
    private volatile boolean running = false;
    private ScheduledFuture<?> pollTask;
    
    public DeviceMonitor(Allowlist allowlist, Properties config) {
        this.allowlist = allowlist;
        this.discoveredDevices = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "DeviceMonitor-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        this.pollIntervalSeconds = getIntProperty(config, "monitor.pollSeconds", 10);
        this.banSeconds = getIntProperty(config, "monitor.banSeconds", 600);
        this.networkScanRange = getIntProperty(config, "monitor.networkScanRange", 254);
        this.pingTimeout = getIntProperty(config, "monitor.pingTimeout", 500);
        this.routerMode = config.getProperty("router.mode", DUMMY_MODE);
        this.openwrtHost = config.getProperty("router.openwrt.host", "192.168.1.1");
        this.openwrtUsername = config.getProperty("router.openwrt.username", "admin");
        this.openwrtPassword = config.getProperty("router.openwrt.password", "");
        
        logger.info("DeviceMonitor initialized with poll interval: " + pollIntervalSeconds + 
                   "s, ban: " + banSeconds + "s, mode: " + routerMode + 
                   ", scan range: " + networkScanRange + ", ping timeout: " + pingTimeout + "ms");
    }
    
    /**
     * Get integer property with default value
     */
    private int getIntProperty(Properties config, String key, int defaultValue) {
        try {
            return Integer.parseInt(config.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warning("Invalid " + key + " value, using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Start monitoring
     */
    public void start() {
        if (running) {
            logger.warning("DeviceMonitor is already running");
            return;
        }
        
        running = true;
        pollTask = scheduler.scheduleAtFixedRate(this::pollDevices, 0, pollIntervalSeconds, TimeUnit.SECONDS);
        logger.info("DeviceMonitor started");
    }
    
    /**
     * Stop monitoring
     */
    public void stop() {
        if (!running) {
            logger.warning("DeviceMonitor is not running");
            return;
        }
        
        running = false;
        if (pollTask != null) {
            pollTask.cancel(false);
        }
        logger.info("DeviceMonitor stopped");
    }
    
    /**
     * Poll devices from Gateway
     */
    private void pollDevices() {
        try {
            if (OPENWRT_MODE.equals(routerMode)) {
                pollFromOpenWrt();
            } else {
                pollFromDummy();
            }
            
            updateDeviceStatus();
            cleanupOldDevices();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during device polling", e);
        }
    }
    
    /**
     * Poll from OpenWrt router
     */
    private void pollFromOpenWrt() {
        try {
            logger.fine("Polling from OpenWrt router at " + openwrtHost);
            // TODO: Implement OpenWrt API calls using openwrtHost, openwrtUsername, openwrtPassword
            // This would typically involve HTTP requests to the router's API endpoints
            logger.fine("OpenWrt polling not fully implemented yet");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error polling from OpenWrt router", e);
        }
    }
    
    /**
     * Poll from dummy mode (local network scan)
     */
    private void pollFromDummy() {
        try {
            String localIp = getLocalIpAddress();
            if (localIp != null) {
                String networkPrefix = getNetworkPrefix(localIp);
                String subnetMask = getSubnetMask();
                String gateway = getGatewayAddress();
                String ipv6Address = getIPv6Address();
                
                logger.info("==========================================");
                logger.info("*** THONG TIN MANG HIEN TAI ***");
                logger.info("==========================================");
                logger.info("IP Local      : " + localIp);
                logger.info("Mang Con      : " + networkPrefix + ".0/" + getSubnetBits(subnetMask));
                logger.info("Subnet Mask   : " + subnetMask);
                logger.info("Bo Dinh Tuyen : " + gateway);
                logger.info("IPv6 Address  : " + (ipv6Address != null ? ipv6Address : "Khong co IPv6"));
                logger.info("==========================================");
                logger.info("");
                logger.info("*** BAT DAU QUET THIET BI ***");
                logger.info("");
                
                scanNetwork(networkPrefix);
            } else {
                logger.warning("Could not determine local IP address for network scanning");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in dummy mode polling", e);
        }
    }
    
    /**
     * Get network prefix from local IP
     */
    private String getNetworkPrefix(String localIp) {
        int lastDotIndex = localIp.lastIndexOf('.');
        return lastDotIndex > 0 ? localIp.substring(0, lastDotIndex) : null;
    }
    
    /**
     * Scan network for devices
     */
    private void scanNetwork(String networkPrefix) {
        if (networkPrefix == null) {
            logger.warning("Cannot scan network: network prefix is null");
            return;
        }
        
        logger.info("==========================================");
        logger.info("*** BAT DAU QUET MANG: " + networkPrefix + ".0/" + networkScanRange + " ***");
        logger.info("==========================================");
        
        int discoveredCount = 0;
        
        // Quét mạng chính
        discoveredCount += scanNetworkRange(networkPrefix, 1, networkScanRange);
        
        // Quét thêm một số mạng con khác để tìm thiết bị
        discoveredCount += scanNetworkRange("10.0.0", 1, 100);
        discoveredCount += scanNetworkRange("172.16.0", 1, 100);
        discoveredCount += scanNetworkRange("192.168.0", 1, 100);
        discoveredCount += scanNetworkRange("192.168.2", 1, 100);
        discoveredCount += scanNetworkRange("192.168.3", 1, 100);
        
        logger.info(""); // Empty line for separation
        logger.info("+--------------------------------------------------+");
        logger.info("|                TONG KET QUET MANG                |");
        logger.info("+--------------------------------------------------+");
        logger.info("| Tong so thiet bi: " + String.format("%-32s", discoveredCount + " thiet bi") + " |");
        logger.info("| Thoi gian quet  : " + String.format("%-32s", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))) + " |");
        logger.info("+--------------------------------------------------+");
        logger.info(""); // Empty line for separation
    }
    
    /**
     * Scan specific network range
     */
    private int scanNetworkRange(String networkPrefix, int start, int end) {
        int count = 0;
        logger.info("Quet mang: " + networkPrefix + "." + start + " - " + networkPrefix + "." + end);
        
        for (int i = start; i <= end; i++) {
            String targetIp = networkPrefix + "." + i;
            if (checkDevice(targetIp)) {
                count++;
                // Giảm delay để quét nhanh hơn khi có nhiều thiết bị
                try {
                    Thread.sleep(30); // Giảm thêm delay để quét nhanh hơn
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("Tim thay " + count + " thiet bi trong mang " + networkPrefix + ".0");
        return count;
    }
    
    /**
     * Check device at specific IP
     */
    private boolean checkDevice(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isReachable(pingTimeout)) {
                String hostname = getDeviceHostname(ip, address);
                String mac = getRealMacAddress(ip);
                
                if (mac != null) {
                    // Use DeviceInfo builder for creation
                    DeviceInfo device = DeviceInfo.builder()
                            .ip(ip)
                            .mac(mac)
                            .hostname(hostname)
                            .known(allowlist.isAllowed(mac))
                            .build();
                    
                    String macKey = mac.toLowerCase();
                    
                    // Update existing device or add new one
                    DeviceInfo existingDevice = discoveredDevices.get(macKey);
                    if (existingDevice != null) {
                        // Create updated device instance
                        DeviceInfo updatedDevice = existingDevice
                                .updateLastSeen()
                                .withKnown(allowlist.isAllowed(mac));
                        discoveredDevices.put(macKey, updatedDevice);
                        logger.info("Updated existing device: " + mac + " at " + ip + " (" + hostname + ")");
                    } else {
                        discoveredDevices.put(macKey, device);
                        
                        // Auto-add new device to allowlist
                        boolean addedToAllowlist = allowlist.autoAddDiscoveredDevice(device);
                        String statusText = addedToAllowlist ? "DA BIET [AUTO-ADDED]" : "CHUA BIET [NEW]";
                        
                        logger.info(""); // Empty line before device info
                        logger.info("+--------------------------------------------------+");
                        logger.info("|                THIET BI MOI                     |");
                        logger.info("+--------------------------------------------------+");
                        logger.info("| IP Address    : " + String.format("%-32s", ip) + " |");
                        logger.info("| MAC Address   : " + String.format("%-32s", mac) + " |");
                        logger.info("| Ten Thiet Bi  : " + String.format("%-32s", hostname) + " |");
                        logger.info("| Trang Thai    : " + String.format("%-32s", statusText) + " |");
                        logger.info("| Thoi Gian     : " + String.format("%-32s", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))) + " |");
                        logger.info("+--------------------------------------------------+");
                        logger.info(""); // Empty line after device info
                    }
                    return true;
                }
            }
        } catch (IOException e) {
            // Device not reachable, ignore silently
        } catch (Exception e) {
            logger.log(Level.FINE, "Error checking device at " + ip, e);
        }
        return false;
    }
    
    /**
     * Get device hostname with fallback strategies
     */
    private String getDeviceHostname(String ip, InetAddress address) {
        try {
            // Strategy 1: Try to get canonical hostname (most reliable)
            String canonicalHostname = address.getCanonicalHostName();
            if (canonicalHostname != null && !canonicalHostname.equals(ip)) {
                logger.fine("Using canonical hostname for " + ip + ": " + canonicalHostname);
                return canonicalHostname;
            }
            
            // Strategy 2: Try to get regular hostname
            String hostname = address.getHostName();
            if (hostname != null && !hostname.equals(ip)) {
                logger.fine("Using regular hostname for " + ip + ": " + hostname);
                return hostname;
            }
            
            // Strategy 3: Try to get device type from common ports/services (faster)
            String deviceType = detectDeviceTypeFast(ip);
            if (deviceType != null) {
                logger.fine("Using port-based device type for " + ip + ": " + deviceType);
                return deviceType;
            }
            
            // Strategy 4: Try reverse DNS lookup
            String reverseDns = getReverseDnsName(ip);
            if (reverseDns != null && !reverseDns.equals(ip)) {
                logger.fine("Using reverse DNS for " + ip + ": " + reverseDns);
                return reverseDns;
            }
            
            // Strategy 5: Generate descriptive name based on IP (last resort)
            String descriptiveName = generateDescriptiveName(ip);
            logger.fine("Using descriptive name for " + ip + ": " + descriptiveName);
            return descriptiveName;
            
        } catch (Exception e) {
            logger.fine("Error getting hostname for " + ip + ": " + e.getMessage());
            return generateDescriptiveName(ip);
        }
    }
    
    /**
     * Detect device type by checking common ports/services (faster version)
     */
    private String detectDeviceTypeFast(String ip) {
        try {
            // Check only the most common ports with shorter timeout
            if (isPortOpenFast(ip, 80) || isPortOpenFast(ip, 443)) {
                return "WebDevice";
            }
            if (isPortOpenFast(ip, 22)) {
                return "SSHDevice";
            }
            if (isPortOpenFast(ip, 23)) {
                return "TelnetDevice";
            }
            if (isPortOpenFast(ip, 21)) {
                return "FTPDevice";
            }
            if (isPortOpenFast(ip, 3389)) {
                return "RDPDevice";
            }
            if (isPortOpenFast(ip, 8080) || isPortOpenFast(ip, 8443)) {
                return "ProxyDevice";
            }
            if (isPortOpenFast(ip, 53)) {
                return "DNSDevice";
            }
            if (isPortOpenFast(ip, 67) || isPortOpenFast(ip, 68)) {
                return "DHCPDevice";
            }
            if (isPortOpenFast(ip, 161) || isPortOpenFast(ip, 162)) {
                return "SNMPDevice";
            }
        } catch (Exception e) {
            // Ignore port check errors
        }
        return null;
    }
    
    /**
     * Detect device type by checking common ports/services (original version)
     */
    private String detectDeviceType(String ip) {
        try {
            // Check common ports to identify device types
            if (isPortOpen(ip, 80) || isPortOpen(ip, 443)) {
                return "WebDevice";
            }
            if (isPortOpen(ip, 22)) {
                return "SSHDevice";
            }
            if (isPortOpen(ip, 23)) {
                return "TelnetDevice";
            }
            if (isPortOpen(ip, 21)) {
                return "FTPDevice";
            }
            if (isPortOpen(ip, 3389)) {
                return "RDPDevice";
            }
            if (isPortOpen(ip, 8080) || isPortOpen(ip, 8443)) {
                return "ProxyDevice";
            }
            if (isPortOpen(ip, 53)) {
                return "DNSDevice";
            }
            if (isPortOpen(ip, 67) || isPortOpen(ip, 68)) {
                return "DHCPDevice";
            }
            if (isPortOpen(ip, 161) || isPortOpen(ip, 162)) {
                return "SNMPDevice";
            }
        } catch (Exception e) {
            // Ignore port check errors
        }
        return null;
    }
    
    /**
     * Check if a port is open on the device (fast version with shorter timeout)
     */
    private boolean isPortOpenFast(String ip, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(ip, port), 300); // 300ms timeout
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if a port is open on the device (original version)
     */
    private boolean isPortOpen(String ip, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(ip, port), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get reverse DNS name for IP address
     */
    private String getReverseDnsName(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            String hostname = address.getCanonicalHostName();
            if (hostname != null && !hostname.equals(ip)) {
                return hostname;
            }
            
            // Try alternative reverse DNS lookup
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                String reverseIp = parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0];
                try {
                    InetAddress reverseAddress = InetAddress.getByName(reverseIp + ".in-addr.arpa");
                    String reverseHostname = reverseAddress.getCanonicalHostName();
                    if (reverseHostname != null && !reverseHostname.equals(reverseIp + ".in-addr.arpa")) {
                        return reverseHostname;
                    }
                } catch (Exception e) {
                    // Ignore reverse DNS errors
                }
            }
        } catch (Exception e) {
            // Ignore DNS lookup errors
        }
        return null;
    }
    
    /**
     * Generate descriptive name based on IP address
     */
    private String generateDescriptiveName(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                int lastOctet = Integer.parseInt(parts[3]);
                
                // Common device naming patterns
                if (lastOctet == 1) {
                    return "Router";
                } else if (lastOctet == 2) {
                    return "Switch";
                } else if (lastOctet == 3) {
                    return "AccessPoint";
                } else if (lastOctet == 6) {
                    return "MSI-PC"; // Your PC
                } else if (lastOctet >= 100 && lastOctet <= 199) {
                    return "Client-" + lastOctet;
                } else if (lastOctet >= 200 && lastOctet <= 254) {
                    return "Device-" + lastOctet;
                } else {
                    return "Node-" + lastOctet;
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return "UnknownDevice";
    }
    
    /**
     * Get local IP address - prioritize real network interfaces
     */
    private String getLocalIpAddress() {
        try {
            // Try to get Wi-Fi IP first (real network)
            String wifiIp = getWifiIpAddress();
            if (wifiIp != null) {
                logger.info("Using Wi-Fi IP for network scanning: " + wifiIp);
                return wifiIp;
            }
            
            // Fallback to default method
            String defaultIp = InetAddress.getLocalHost().getHostAddress();
            logger.info("Using default IP for network scanning: " + defaultIp);
            return defaultIp;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not get local IP address", e);
            return null;
        }
    }
    
    /**
     * Get Wi-Fi IP address if available
     */
    private String getWifiIpAddress() {
        try {
            // Try common Wi-Fi network ranges
            String[] wifiRanges = {"192.168.1", "192.168.0", "10.0.0", "172.16.0"};
            
            for (String range : wifiRanges) {
                for (int i = 1; i <= 10; i++) {
                    String testIp = range + "." + i;
                    try {
                        InetAddress address = InetAddress.getByName(testIp);
                        if (address.isReachable(1000)) {
                            // Found a reachable IP in Wi-Fi range
                            String localIp = getLocalIpInRange(range);
                            if (localIp != null) {
                                return localIp;
                            }
                        }
                    } catch (Exception e) {
                        // Continue to next IP
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Error detecting Wi-Fi network: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get local IP in specific network range
     */
    private String getLocalIpInRange(String networkPrefix) {
        try {
            for (int i = 1; i <= 254; i++) {
                String testIp = networkPrefix + "." + i;
                try {
                    InetAddress address = InetAddress.getByName(testIp);
                    if (address.isReachable(500) && address.getHostAddress().equals(testIp)) {
                        // This is our local IP
                        return testIp;
                    }
                } catch (Exception e) {
                    // Continue to next IP
                }
            }
        } catch (Exception e) {
            logger.fine("Error finding local IP in range " + networkPrefix + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get subnet mask from network interface
     */
    private String getSubnetMask() {
        try {
            // Try to get subnet mask from network interface
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress address = addresses.nextElement();
                        if (address instanceof java.net.Inet4Address) {
                            // For Windows, we'll use a common subnet mask
                            // In a real implementation, you'd use JNA or native calls
                            return "255.255.255.0"; // Common /24 network
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Error getting subnet mask: " + e.getMessage());
        }
        return "255.255.255.0"; // Default fallback
    }
    
    /**
     * Get gateway address (router IP)
     */
    private String getGatewayAddress() {
        try {
            // Try to get gateway from route table
            ProcessBuilder pb = new ProcessBuilder("route", "print");
            Process process = pb.start();
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // Look for default gateway (0.0.0.0)
                    if (line.contains("0.0.0.0") && line.contains("0.0.0.0")) {
                        String[] parts = line.split("\\s+");
                        for (int i = 0; i < parts.length - 1; i++) {
                            if (parts[i].equals("0.0.0.0") && parts[i + 1].matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                return parts[i + 1];
                            }
                        }
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.fine("Route command failed with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            logger.fine("Error getting gateway address: " + e.getMessage());
        }
        
        // Fallback to common gateway
        String localIp = getLocalIpAddress();
        if (localIp != null) {
            String networkPrefix = getNetworkPrefix(localIp);
            return networkPrefix + ".1"; // Common gateway pattern
        }
        
        return "192.168.1.1"; // Default fallback
    }
    
    /**
     * Get IPv6 address from network interface
     */
    private String getIPv6Address() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress address = addresses.nextElement();
                        if (address instanceof java.net.Inet6Address && !address.isLinkLocalAddress()) {
                            return address.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Error getting IPv6 address: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get subnet bits from subnet mask
     */
    private int getSubnetBits(String subnetMask) {
        try {
            String[] parts = subnetMask.split("\\.");
            int bits = 0;
            for (String part : parts) {
                int octet = Integer.parseInt(part);
                bits += Integer.bitCount(octet);
            }
            return bits;
        } catch (Exception e) {
            logger.fine("Error calculating subnet bits: " + e.getMessage());
            return 24; // Default /24 network
        }
    }
    
    /**
     * Get real MAC address from ARP table or network interface
     */
    private String getRealMacAddress(String ip) {
        try {
            // Strategy 1: Try to get MAC from ARP table
            String arpMac = getMacFromArpTable(ip);
            if (arpMac != null) {
                logger.fine("Using ARP table MAC for " + ip + ": " + arpMac);
                return arpMac;
            }
            
            // Strategy 2: Try to get MAC from network interface
            String interfaceMac = getMacFromNetworkInterface(ip);
            if (interfaceMac != null) {
                logger.fine("Using interface MAC for " + ip + ": " + interfaceMac);
                return interfaceMac;
            }
            
            // Strategy 3: Fallback to generated MAC (last resort)
            String generatedMac = generateMacAddress(ip);
            logger.fine("Using generated MAC for " + ip + ": " + generatedMac);
            return generatedMac;
            
        } catch (Exception e) {
            logger.log(Level.FINE, "Error getting MAC for " + ip + ": " + e.getMessage());
            return generateMacAddress(ip);
        }
    }
    
    /**
     * Get MAC address from ARP table using system command
     */
    private String getMacFromArpTable(String ip) {
        try {
            // Use arp -a command to get MAC from ARP table
            ProcessBuilder pb = new ProcessBuilder("arp", "-a", ip);
            Process process = pb.start();
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // Parse ARP table output
                    if (line.contains(ip)) {
                        // Extract MAC address from ARP output
                        String[] parts = line.split("\\s+");
                        for (String part : parts) {
                            if (part.matches("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")) {
                                return part.toUpperCase();
                            }
                        }
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.fine("ARP command failed with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            logger.fine("Error getting MAC from ARP table for " + ip + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get MAC address from network interface
     */
    private String getMacFromNetworkInterface(String ip) {
        try {
            // Try to get MAC from local network interfaces
            java.net.NetworkInterface networkInterface = java.net.NetworkInterface.getByInetAddress(
                InetAddress.getByName(ip));
            
            if (networkInterface != null) {
                byte[] macBytes = networkInterface.getHardwareAddress();
                if (macBytes != null && macBytes.length == 6) {
                    return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                        macBytes[0], macBytes[1], macBytes[2], macBytes[3], macBytes[4], macBytes[5]);
                }
            }
        } catch (Exception e) {
            logger.fine("Error getting MAC from network interface for " + ip + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Generate MAC address from IP (simplified approach - fallback)
     */
    private String generateMacAddress(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                int lastOctet = Integer.parseInt(parts[3]);
                // Generate a deterministic but unique MAC based on IP
                return String.format("00:1B:44:%02X:%02X:%02X", 
                    lastOctet, lastOctet + 1, lastOctet + 2);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Error generating MAC for IP: " + ip, e);
        }
        return null;
    }
    
    /**
     * Update known/unknown status for devices
     */
    private void updateDeviceStatus() {
        int knownCount = 0;
        int unknownCount = 0;
        
        // Create new map with updated devices
        Map<String, DeviceInfo> updatedDevices = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, DeviceInfo> entry : discoveredDevices.entrySet()) {
            String macKey = entry.getKey();
            DeviceInfo device = entry.getValue();
            boolean isKnown = allowlist.isAllowed(device.getMac());
            
            // Create updated device instance
            DeviceInfo updatedDevice = device
                    .withKnown(isKnown)
                    .updateLastSeen();
            
            updatedDevices.put(macKey, updatedDevice);
            
            if (isKnown) {
                knownCount++;
            } else {
                unknownCount++;
            }
        }
        
        // Replace the map with updated devices
        discoveredDevices.clear();
        discoveredDevices.putAll(updatedDevices);
        
        logger.fine("Device status updated: " + knownCount + " known, " + unknownCount + " unknown");
    }
    
    /**
     * Clean up old inactive devices
     */
    private void cleanupOldDevices() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(banSeconds);
        int initialCount = discoveredDevices.size();
        
        discoveredDevices.entrySet().removeIf(entry -> {
            DeviceInfo device = entry.getValue();
            if (device.getLastSeen().isBefore(cutoff)) {
                logger.fine("Removing old device: " + device.getMac() + " (last seen: " + device.getLastSeen() + ")");
                return true;
            }
            return false;
        });
        
        int removedCount = initialCount - discoveredDevices.size();
        if (removedCount > 0) {
            logger.info("Cleaned up " + removedCount + " old devices");
        }
    }
    
    /**
     * Get all discovered devices
     */
    public List<DeviceInfo> getAllDevices() {
        return new ArrayList<>(discoveredDevices.values());
    }
    
    /**
     * Get device by MAC address
     */
    public DeviceInfo getDevice(String mac) {
        if (mac == null) return null;
        return discoveredDevices.get(mac.toLowerCase());
    }
    
    /**
     * Get device count
     */
    public int getDeviceCount() {
        return discoveredDevices.size();
    }
    
    /**
     * Check if device is active
     */
    public boolean isDeviceActive(String mac) {
        if (mac == null) return false;
        
        DeviceInfo device = discoveredDevices.get(mac.toLowerCase());
        if (device == null) return false;
        
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(pollIntervalSeconds * 2);
        return device.getLastSeen().isAfter(cutoff);
    }
    
    /**
     * Shutdown monitor
     */
    public void shutdown() {
        stop();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) { // Changed to 5 seconds for consistency
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("DeviceMonitor shutdown completed");
    }
}
