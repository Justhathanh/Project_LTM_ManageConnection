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
        
        this.pollIntervalSeconds = getIntProperty(config, "monitor.pollSeconds", 5);
        this.banSeconds = getIntProperty(config, "monitor.banSeconds", 600);
        this.networkScanRange = getIntProperty(config, "monitor.networkScanRange", 254);
        this.pingTimeout = getIntProperty(config, "monitor.pingTimeout", 1000);
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
                scanNetwork(networkPrefix);
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
        
        logger.fine("Scanning network: " + networkPrefix + ".0/" + networkScanRange);
        
        for (int i = 1; i <= networkScanRange; i++) {
            String targetIp = networkPrefix + "." + i;
            checkDevice(targetIp);
        }
        
        logger.fine("Network scan completed for " + networkScanRange + " addresses");
    }
    
    /**
     * Check device at specific IP
     */
    private void checkDevice(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isReachable(pingTimeout)) {
                String hostname = address.getHostName();
                String mac = generateMacAddress(ip);
                
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
                        logger.fine("Updated existing device: " + mac + " at " + ip);
                    } else {
                        discoveredDevices.put(macKey, device);
                        logger.fine("Discovered new device: " + mac + " at " + ip + " (" + hostname + ")");
                    }
                }
            }
        } catch (IOException e) {
            // Device not reachable, ignore silently
        } catch (Exception e) {
            logger.log(Level.FINE, "Error checking device at " + ip, e);
        }
    }
    
    /**
     * Get local IP address
     */
    private String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not get local IP address", e);
            return null;
        }
    }
    
    /**
     * Generate MAC address from IP (simplified approach)
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
            logger.log(Level.FINE, "Could not generate MAC for IP: " + ip, e);
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
