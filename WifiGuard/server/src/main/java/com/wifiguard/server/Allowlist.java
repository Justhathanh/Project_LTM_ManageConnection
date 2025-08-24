package com.wifiguard.server;

<<<<<<< HEAD
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.wifiguard.server.model.DeviceInfo;

/**
 * Enhanced allowlist manager with improved validation and device handling
 */
public class Allowlist {
    private static final Logger logger = Logger.getLogger(Allowlist.class.getName());
    private static final String ALLOWLIST_FILE = "allowlist.txt";
    private static final String COMMENT_PREFIX = "#";
    private static final String FIELD_SEPARATOR = ",";
    
    private final Map<String, DeviceInfo> allowedDevices;
    private final Path allowlistPath;
    
    public Allowlist() {
        this.allowedDevices = new ConcurrentHashMap<>();
        this.allowlistPath = Paths.get(ALLOWLIST_FILE);
        loadAllowlist();
    }
    
    /**
     * Load devices from file
     */
    public void loadAllowlist() {
        try {
            if (Files.exists(allowlistPath)) {
                loadFromFile();
            } else {
                logger.info("Allowlist file not found, creating new one");
                saveAllowlist();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error loading allowlist", e);
        }
    }
    
    /**
     * Load devices from existing file
     */
    private void loadFromFile() throws IOException {
        List<String> lines = Files.readAllLines(allowlistPath);
        allowedDevices.clear();
        
        int loadedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;
        
        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1);
            
            if (isValidLine(line)) {
                try {
                    DeviceInfo device = parseDeviceLine(line);
                    if (device != null && device.isValid()) {
                        String macKey = device.getMac().toLowerCase();
                        if (allowedDevices.containsKey(macKey)) {
                            logger.warning("Duplicate MAC address at line " + lineNumber + ": " + device.getMac());
                            skippedCount++;
                        } else {
                            allowedDevices.put(macKey, device);
                            loadedCount++;
                        }
                    } else {
                        String errors = device != null ? device.getValidationErrors() : "Parse failed";
                        logger.warning("Invalid device data at line " + lineNumber + ": " + line + " - " + errors);
                        errorCount++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error parsing line " + lineNumber + ": " + line, e);
                    errorCount++;
                }
            }
        }
        
        logger.info("Allowlist loaded: " + loadedCount + " devices, " + skippedCount + " skipped, " + errorCount + " errors");
    }
    
    /**
     * Check if line is valid for processing
     */
    private boolean isValidLine(String line) {
        String trimmed = line.trim();
        return !trimmed.isEmpty() && !trimmed.startsWith(COMMENT_PREFIX);
    }
    
    /**
     * Parse device information from line using enhanced DeviceInfo
     */
    private DeviceInfo parseDeviceLine(String line) {
        try {
            String[] parts = line.split(FIELD_SEPARATOR);
            if (parts.length < 1) return null;
            
            String mac = parts[0].trim();
            String hostname = parts.length > 1 ? parts[1].trim() : "Unknown";
            String ip = parts.length > 2 ? parts[2].trim() : "";
            
            // Use DeviceInfo builder for validation
            return DeviceInfo.builder()
                    .mac(mac)
                    .hostname(hostname)
                    .ip(ip)
                    .known(true) // Devices in allowlist are known
                    .buildOrNull();
                    
        } catch (Exception e) {
            logger.log(Level.FINE, "Error parsing device line: " + line, e);
            return null;
        }
    }
    
    /**
     * Save allowlist to file
     */
    public void saveAllowlist() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# WifiGuard Allowlist - Format: MAC,HOSTNAME,IP");
            lines.add("# Generated: " + java.time.LocalDateTime.now());
            lines.add("");
            
            // Convert devices to lines
            List<String> deviceLines = allowedDevices.values().stream()
                    .map(this::deviceToLine)
                    .collect(Collectors.toList());
            
            lines.addAll(deviceLines);
            
            Files.write(allowlistPath, lines);
            logger.info("Allowlist saved with " + allowedDevices.size() + " devices");
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error saving allowlist", e);
            throw new RuntimeException("Failed to save allowlist", e);
        }
    }
    
    /**
     * Convert device to line format
     */
    private String deviceToLine(DeviceInfo device) {
        return String.join(FIELD_SEPARATOR, 
                device.getMac(),
                device.getHostname(),
                device.getIp());
    }
    
    /**
     * Add device to allowlist
     */
    public boolean addDevice(DeviceInfo device) {
        if (device == null || !device.isValid()) {
            logger.warning("Cannot add invalid device: " + (device != null ? device.getValidationErrors() : "null"));
            return false;
        }
        
        String macKey = device.getMac().toLowerCase();
        
        if (allowedDevices.containsKey(macKey)) {
            logger.info("Device already exists in allowlist: " + device.getMac());
            return false;
        }
        
        // Create a new device instance with known=true
        DeviceInfo allowlistDevice = device.withKnown(true);
        allowedDevices.put(macKey, allowlistDevice);
        
        logger.info("Device added to allowlist: " + device.toCompactString());
        
        // Save to file
        try {
            saveAllowlist();
            return true;
        } catch (Exception e) {
            // Rollback on save failure
            allowedDevices.remove(macKey);
            logger.log(Level.SEVERE, "Failed to save allowlist after adding device, rolling back", e);
            return false;
        }
    }
    
    /**
     * Remove device from allowlist
     */
    public boolean removeDevice(String mac) {
        if (mac == null || mac.trim().isEmpty()) {
            logger.warning("Cannot remove device with null/empty MAC");
            return false;
        }
        
        String macKey = mac.trim().toLowerCase();
        DeviceInfo removed = allowedDevices.remove(macKey);
        
        if (removed == null) {
            logger.info("Device not found in allowlist: " + mac);
            return false;
        }
        
        logger.info("Device removed from allowlist: " + removed.toCompactString());
        
        // Save to file
        try {
            saveAllowlist();
            return true;
        } catch (Exception e) {
            // Rollback on save failure
            allowedDevices.put(macKey, removed);
            logger.log(Level.SEVERE, "Failed to save allowlist after removing device, rolling back", e);
            return false;
        }
    }
    
    /**
     * Check if device is allowed
     */
    public boolean isAllowed(String mac) {
        if (mac == null || mac.trim().isEmpty()) return false;
        return allowedDevices.containsKey(mac.trim().toLowerCase());
    }
    
    /**
     * Get device from allowlist
     */
    public DeviceInfo getDevice(String mac) {
        if (mac == null || mac.trim().isEmpty()) return null;
        return allowedDevices.get(mac.trim().toLowerCase());
    }
    
    /**
     * Get all allowed devices
     */
    public List<DeviceInfo> getAllDevices() {
        return new ArrayList<>(allowedDevices.values());
    }
    
    /**
     * Get device count
     */
    public int getDeviceCount() {
        return allowedDevices.size();
    }
    
    /**
     * Check if allowlist is empty
     */
    public boolean isEmpty() {
        return allowedDevices.isEmpty();
    }
    
    /**
     * Clear all devices from allowlist
     */
    public void clear() {
        int count = allowedDevices.size();
        allowedDevices.clear();
        logger.info("Allowlist cleared, removed " + count + " devices");
        
        try {
            saveAllowlist();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to save empty allowlist", e);
        }
    }
    
    /**
     * Get devices by hostname pattern
     */
    public List<DeviceInfo> getDevicesByHostname(String hostnamePattern) {
        if (hostnamePattern == null || hostnamePattern.trim().isEmpty()) {
            return getAllDevices();
        }
        
        String pattern = hostnamePattern.toLowerCase();
        return allowedDevices.values().stream()
                .filter(device -> device.getHostname().toLowerCase().contains(pattern))
                .collect(Collectors.toList());
    }
    
    /**
     * Get devices by IP pattern
     */
    public List<DeviceInfo> getDevicesByIp(String ipPattern) {
        if (ipPattern == null || ipPattern.trim().isEmpty()) {
            return getAllDevices();
        }
        
        String pattern = ipPattern.toLowerCase();
        return allowedDevices.values().stream()
                .filter(device -> device.getIp().toLowerCase().contains(pattern))
                .collect(Collectors.toList());
    }
    
    /**
     * Get allowlist statistics
     */
    public String getStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Allowlist Statistics:\n");
        stats.append("- Total devices: ").append(getDeviceCount()).append("\n");
        stats.append("- File path: ").append(allowlistPath.toAbsolutePath()).append("\n");
        
        if (!isEmpty()) {
            long validDevices = allowedDevices.values().stream()
                    .filter(DeviceInfo::isValid)
                    .count();
            long invalidDevices = getDeviceCount() - validDevices;
            
            stats.append("- Valid devices: ").append(validDevices).append("\n");
            if (invalidDevices > 0) {
                stats.append("- Invalid devices: ").append(invalidDevices).append("\n");
            }
        }
        
        return stats.toString();
    }
    
    /**
     * Validate all devices in allowlist
     */
    public List<String> validateAllDevices() {
        List<String> errors = new ArrayList<>();
        
        allowedDevices.forEach((mac, device) -> {
            if (!device.isValid()) {
                errors.add("Device " + mac + ": " + device.getValidationErrors());
            }
        });
        
        return errors;
    }
    
    /**
     * Get allowlist summary for logging
     */
    @Override
    public String toString() {
        return "Allowlist{size=" + getDeviceCount() + ", file=" + allowlistPath.getFileName() + "}";
    }
=======
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class Allowlist {
  private final File file;
  private final Set<String> set = new ConcurrentSkipListSet<>();

  public Allowlist(File file) throws IOException { this.file = file; load(); }
  public synchronized void load() throws IOException {
    set.clear();
    if (!file.exists()) return;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      String line; while ((line = br.readLine()) != null) {
        line = line.trim(); if (line.isEmpty() || line.startsWith("#")) continue;
        set.add(line.toLowerCase());
      }
    }
  }
  public synchronized void add(String mac) throws IOException { set.add(mac.toLowerCase()); save(); }
  public synchronized void remove(String mac) throws IOException { set.remove(mac.toLowerCase()); save(); }
  public boolean contains(String mac) { return set.contains(mac.toLowerCase()); }
  public Collection<String> all() { return Collections.unmodifiableCollection(set); }
  private synchronized void save() throws IOException {
    try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
      bw.write("# allowlist\n");
      for (String m : set) { bw.write(m); bw.write('\n'); }
    }
  }
>>>>>>> 8a2bbaf (them file allowlist de doc file)
}
