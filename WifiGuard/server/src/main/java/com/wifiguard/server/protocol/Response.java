package com.wifiguard.server.protocol;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.wifiguard.server.model.DeviceInfo;

/**
 * Response model returned to client with enhanced formatting and performance
 */
public class Response {
    public enum Status {
        SUCCESS,
        ERROR,
        INVALID_COMMAND,
        DEVICE_NOT_FOUND,
        DEVICE_ALREADY_EXISTS
    }
    
    // Constants
    private static final String EMPTY_MESSAGE = "";
    private static final String DEFAULT_DATA = "";
    private static final String FIELD_SEPARATOR = "|";
    private static final String KEY_VALUE_SEPARATOR = ":";
    private static final String DEVICE_SEPARATOR = ";";
    private static final String DEVICE_FIELD_SEPARATOR = ",";
    
    private final Status status;
    private final String message;
    private final List<DeviceInfo> devices;
    private final String data;
    
    // Private constructor to enforce factory method usage
    private Response(Status status, String message, List<DeviceInfo> devices, String data) {
        this.status = status;
        this.message = message != null ? message : EMPTY_MESSAGE;
        this.devices = devices != null ? Collections.unmodifiableList(devices) : Collections.emptyList();
        this.data = data != null ? data : DEFAULT_DATA;
    }
    
    // Getters
    public Status getStatus() { 
        return status; 
    }
    
    public String getMessage() { 
        return message; 
    }
    
    public List<DeviceInfo> getDevices() { 
        return devices; 
    }
    
    public String getData() { 
        return data; 
    }
    
    // Factory methods for common responses
    public static Response success(String message) {
        return new Response(Status.SUCCESS, message, null, null);
    }
    
    public static Response success(String message, List<DeviceInfo> devices) {
        return new Response(Status.SUCCESS, message, devices, null);
    }
    
    public static Response success(String message, String data) {
        return new Response(Status.SUCCESS, message, null, data);
    }
    
    public static Response error(String message) {
        return new Response(Status.ERROR, message, null, null);
    }
    
    public static Response invalidCommand(String message) {
        return new Response(Status.INVALID_COMMAND, message, null, null);
    }
    
    public static Response deviceNotFound(String message) {
        return new Response(Status.DEVICE_NOT_FOUND, message, null, null);
    }
    
    public static Response deviceAlreadyExists(String message) {
        return new Response(Status.DEVICE_ALREADY_EXISTS, message, null, null);
    }
    
    /**
     * Check if response is successful
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    /**
     * Check if response has devices
     */
    public boolean hasDevices() {
        return !devices.isEmpty();
    }
    
    /**
     * Check if response has data
     */
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }
    
    /**
     * Get device count
     */
    public int getDeviceCount() {
        return devices.size();
    }
    
    /**
     * Get formatted response for client
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Status field
        sb.append("STATUS").append(KEY_VALUE_SEPARATOR).append(status.name());
        
        // Message field
        if (!message.isEmpty()) {
            sb.append(FIELD_SEPARATOR).append("MESSAGE").append(KEY_VALUE_SEPARATOR)
              .append(escapeSpecialChars(message));
        }
        
        // Data field
        if (!data.isEmpty()) {
            sb.append(FIELD_SEPARATOR).append("DATA").append(KEY_VALUE_SEPARATOR)
              .append(escapeSpecialChars(data));
        }
        
        // Devices field
        if (!devices.isEmpty()) {
            sb.append(FIELD_SEPARATOR).append("DEVICES").append(KEY_VALUE_SEPARATOR)
              .append(devices.size());
            sb.append(FIELD_SEPARATOR).append("DEVICE_LIST").append(KEY_VALUE_SEPARATOR)
              .append(formatDeviceList());
        }
        
        return sb.toString();
    }
    
    /**
     * Get human-readable formatted message
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        
        // Status indicator
        switch (status) {
            case SUCCESS:
                sb.append("✓ ");
                break;
            case ERROR:
                sb.append("✗ ");
                break;
            case INVALID_COMMAND:
                sb.append("⚠ ");
                break;
            case DEVICE_NOT_FOUND:
                sb.append("🔍 ");
                break;
            case DEVICE_ALREADY_EXISTS:
                sb.append("⚠ ");
                break;
            default:
                sb.append("• ");
        }
        
        sb.append(message);
        
        // Add device count if available
        if (hasDevices()) {
            sb.append(" (").append(devices.size()).append(" devices)");
        }
        
        return sb.toString();
    }
    
    /**
     * Format device list for response
     */
    private String formatDeviceList() {
        if (devices.isEmpty()) {
            return "";
        }
        
        return devices.stream()
                .map(this::formatDevice)
                .collect(Collectors.joining(DEVICE_SEPARATOR));
    }
    
    /**
     * Format individual device for response
     */
    private String formatDevice(DeviceInfo device) {
        StringBuilder sb = new StringBuilder();
        
        // MAC address
        sb.append("MAC").append(KEY_VALUE_SEPARATOR).append(device.getMac());
        
        // IP address
        if (device.getIp() != null && !device.getIp().isEmpty()) {
            sb.append(DEVICE_FIELD_SEPARATOR).append("IP").append(KEY_VALUE_SEPARATOR).append(device.getIp());
        }
        
        // Hostname
        if (device.getHostname() != null && !device.getHostname().isEmpty()) {
            sb.append(DEVICE_FIELD_SEPARATOR).append("HOSTNAME").append(KEY_VALUE_SEPARATOR).append(device.getHostname());
        }
        
        // Known status
        sb.append(DEVICE_FIELD_SEPARATOR).append("KNOWN").append(KEY_VALUE_SEPARATOR).append(device.isKnown());
        
        // Last seen timestamp
        if (device.getLastSeen() != null) {
            sb.append(DEVICE_FIELD_SEPARATOR).append("LAST_SEEN").append(KEY_VALUE_SEPARATOR).append(device.getLastSeen());
        }
        
        return sb.toString();
    }
    
    /**
     * Escape special characters in strings
     */
    private String escapeSpecialChars(String input) {
        if (input == null) return "";
        
        return input.replace("\\", "\\\\")
                   .replace("|", "\\|")
                   .replace(":", "\\:")
                   .replace(";", "\\;")
                   .replace(",", "\\,");
    }
    
    /**
     * Get compact response (minimal formatting)
     */
    public String toCompactString() {
        if (devices.isEmpty()) {
            return status.name() + KEY_VALUE_SEPARATOR + message;
        }
        
        return status.name() + KEY_VALUE_SEPARATOR + message + 
               FIELD_SEPARATOR + "Devices: " + devices.size();
    }
    
    /**
     * Get JSON-like response format
     */
    public String toJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        // Status
        sb.append("\"status\":\"").append(status.name()).append("\"");
        
        // Message
        if (!message.isEmpty()) {
            sb.append(",\"message\":\"").append(escapeJsonString(message)).append("\"");
        }
        
        // Data
        if (!data.isEmpty()) {
            sb.append(",\"data\":\"").append(escapeJsonString(data)).append("\"");
        }
        
        // Devices
        if (!devices.isEmpty()) {
            sb.append(",\"deviceCount\":").append(devices.size());
            sb.append(",\"devices\":[");
            
            String deviceJson = devices.stream()
                    .map(this::deviceToJson)
                    .collect(Collectors.joining(","));
            sb.append(deviceJson);
            
            sb.append("]");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Convert device to JSON format
     */
    private String deviceToJson(DeviceInfo device) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        sb.append("\"mac\":\"").append(device.getMac()).append("\"");
        
        if (device.getIp() != null && !device.getIp().isEmpty()) {
            sb.append(",\"ip\":\"").append(device.getIp()).append("\"");
        }
        
        if (device.getHostname() != null && !device.getHostname().isEmpty()) {
            sb.append(",\"hostname\":\"").append(device.getHostname()).append("\"");
        }
        
        sb.append(",\"known\":").append(device.isKnown());
        
        if (device.getLastSeen() != null) {
            sb.append(",\"lastSeen\":\"").append(device.getLastSeen()).append("\"");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Escape special characters for JSON
     */
    private String escapeJsonString(String input) {
        if (input == null) return "";
        
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Check if response contains specific status
     */
    public boolean hasStatus(Status expectedStatus) {
        return status == expectedStatus;
    }
    
    /**
     * Check if response contains error
     */
    public boolean isError() {
        return status != Status.SUCCESS;
    }
    
    /**
     * Get response size in bytes (approximate)
     */
    public int getResponseSize() {
        return toString().getBytes().length;
    }
    
    /**
     * Get response summary
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(status.name());
        
        if (!message.isEmpty()) {
            sb.append(": ").append(message);
        }
        
        if (hasDevices()) {
            sb.append(" (").append(devices.size()).append(" devices)");
        }
        
        return sb.toString();
    }
}
