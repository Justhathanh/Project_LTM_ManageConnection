package com.wifiguard.server.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Model bất biến chứa thông tin về thiết bị mạng
 * Sử dụng builder pattern để tạo và kiểm tra linh hoạt
 */
public final class DeviceInfo {
    // Hằng số
    private static final String UNKNOWN_HOSTNAME = "Unknown";
    private static final String UNKNOWN_IP = "";
    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    private static final Pattern IP_PATTERN = Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Các trường bất biến
    private final String ip;
    private final String mac;
    private final String hostname;
    private final LocalDateTime lastSeen;
    private final boolean known;
    
    // Constructor riêng tư - sử dụng builder
    private DeviceInfo(String ip, String mac, String hostname, LocalDateTime lastSeen, boolean known) {
        this.ip = ip != null ? ip : UNKNOWN_IP;
        this.mac = mac;
        this.hostname = hostname != null ? hostname : UNKNOWN_HOSTNAME;
        this.lastSeen = lastSeen != null ? lastSeen : LocalDateTime.now();
        this.known = known;
    }
    
    // Các getter - tất cả đều bất biến
    public String getIp() { return ip; }
    public String getMac() { return mac; }
    public String getHostname() { return hostname; }
    public LocalDateTime getLastSeen() { return lastSeen; }
    public boolean isKnown() { return known; }
    
    // Các phương thức tiện ích
    public boolean hasValidMac() {
        return mac != null && MAC_PATTERN.matcher(mac).matches();
    }
    
    public boolean hasIp() {
        return ip != null && !ip.trim().isEmpty();
    }
    
    public boolean hasValidIp() {
        return hasIp() && IP_PATTERN.matcher(ip).matches();
    }
    
    public boolean isActive(int timeoutSeconds) {
        if (lastSeen == null) return false;
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        return lastSeen.isAfter(cutoff);
    }
    
    public String getFormattedLastSeen() {
        return lastSeen != null ? lastSeen.format(TIMESTAMP_FORMATTER) : "Never";
    }
    
    public long getAgeInSeconds() {
        if (lastSeen == null) return Long.MAX_VALUE;
        return java.time.Duration.between(lastSeen, LocalDateTime.now()).getSeconds();
    }
    
    public boolean isExpired(int maxAgeSeconds) {
        return getAgeInSeconds() > maxAgeSeconds;
    }
    
    // Builder pattern để tạo linh hoạt
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String ip = UNKNOWN_IP;
        private String mac;
        private String hostname = UNKNOWN_HOSTNAME;
        private LocalDateTime lastSeen = LocalDateTime.now();
        private boolean known = false;
        
        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }
        
        public Builder mac(String mac) {
            this.mac = mac;
            return this;
        }
        
        public Builder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }
        
        public Builder lastSeen(LocalDateTime lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }
        
        public Builder known(boolean known) {
            this.known = known;
            return this;
        }
        
        public Builder fromExisting(DeviceInfo existing) {
            this.ip = existing.ip;
            this.mac = existing.mac;
            this.hostname = existing.hostname;
            this.lastSeen = existing.lastSeen;
            this.known = existing.known;
            return this;
        }
        
        public DeviceInfo build() {
            if (mac == null || mac.trim().isEmpty()) {
                throw new IllegalArgumentException("MAC address is required");
            }
            return new DeviceInfo(ip, mac, hostname, lastSeen, known);
        }
        
        public DeviceInfo buildOrNull() {
            try {
                return build();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
    
    // Các phương thức factory cho các trường hợp phổ biến
    public static DeviceInfo createUnknown() {
        return new DeviceInfo(UNKNOWN_IP, null, UNKNOWN_HOSTNAME, LocalDateTime.now(), false);
    }
    
    public static DeviceInfo createFromMac(String mac) {
        return new DeviceInfo(UNKNOWN_IP, mac, UNKNOWN_HOSTNAME, LocalDateTime.now(), false);
    }
    
    public static DeviceInfo createFromMacAndIp(String mac, String ip) {
        return new DeviceInfo(ip, mac, UNKNOWN_HOSTNAME, LocalDateTime.now(), false);
    }
    
    // Các phương thức cập nhật bất biến
    public DeviceInfo withIp(String newIp) {
        return new DeviceInfo(newIp, mac, hostname, lastSeen, known);
    }
    
    public DeviceInfo withHostname(String newHostname) {
        return new DeviceInfo(ip, mac, newHostname, lastSeen, known);
    }
    
    public DeviceInfo withLastSeen(LocalDateTime newLastSeen) {
        return new DeviceInfo(ip, mac, hostname, newLastSeen, known);
    }
    
    public DeviceInfo withKnown(boolean newKnown) {
        return new DeviceInfo(ip, mac, hostname, lastSeen, newKnown);
    }
    
    public DeviceInfo updateLastSeen() {
        return new DeviceInfo(ip, mac, hostname, LocalDateTime.now(), known);
    }
    
    // Các phương thức kiểm tra
    public boolean isValid() {
        return hasValidMac() && (ip.isEmpty() || hasValidIp());
    }
    
    public String getValidationErrors() {
        StringBuilder errors = new StringBuilder();
        
        if (!hasValidMac()) {
            errors.append("Invalid MAC address format; ");
        }
        
        if (hasIp() && !hasValidIp()) {
            errors.append("Invalid IP address format; ");
        }
        
        return errors.length() > 0 ? errors.toString().trim() : "Valid";
    }
    
    // Các phương thức Object
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceInfo that = (DeviceInfo) o;
        return Objects.equals(mac, that.mac);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(mac);
    }
    
    @Override
    public String toString() {
        return String.format("DeviceInfo{ip='%s', mac='%s', hostname='%s', lastSeen=%s, known=%s}",
                ip, mac, hostname, getFormattedLastSeen(), known);
    }
    
    // Định dạng compact cho logging
    public String toCompactString() {
        return String.format("%s(%s)@%s", hostname, mac, ip);
    }
    
    // Định dạng JSON cho API responses
    public String toJsonString() {
        return String.format("{\"mac\":\"%s\",\"ip\":\"%s\",\"hostname\":\"%s\",\"lastSeen\":\"%s\",\"known\":%s}",
                mac, ip, hostname, getFormattedLastSeen(), known);
    }
}
