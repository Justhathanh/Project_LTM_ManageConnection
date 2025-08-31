# Vấn đề với nút TLS trong giao diện WifiGuard

## **Mô tả vấn đề:**
Khi chọn nút TLS trong giao diện, hệ thống bị lỗi load thiết bị. Vấn đề này xảy ra do:

1. **TLS Server không được khởi tạo đúng cách** trong `ServerMain.java`
2. **Cấu hình TLS không khớp** giữa các file
3. **Thiếu keystore** để xác thực SSL/TLS
4. **TlsServer không có method shutdown** để dọn dẹp tài nguyên

## **Nguyên nhân chính:**

### 1. **Thiếu khởi tạo TlsServer**
- Trong `ServerMain.java` chỉ có `TcpServer` được khởi tạo
- Không có logic để khởi tạo `TlsServer` khi TLS được bật

### 2. **Cấu hình không nhất quán**
- `server.properties` sử dụng `tls.enabled`
- `SecurityConfig.java` tìm kiếm `server.tls.enabled`
- Port TLS không được cấu hình rõ ràng

### 3. **Keystore thiếu**
- Không có file `server.jks` để xác thực SSL
- `keytool` không có sẵn trong PATH

## **Giải pháp đã thực hiện:**

### 1. **Cập nhật ServerMain.java**
```java
// Thêm TlsServer vào components
private final TlsServer tlsServer;

// Khởi tạo TlsServer khi TLS được bật
if (isTlsEnabled()) {
    ServerConfig serverConfig = new ServerConfig();
    SecurityConfig securityConfig = new SecurityConfig(serverConfig);
    this.tlsServer = new TlsServer(getTlsPort(), securityConfig, deviceMonitor, allowlist);
}

// Start TlsServer trong startComponents()
if (tlsServer != null) {
    tlsServer.start();
}

// Shutdown TlsServer trong shutdown()
if (tlsServer != null) {
    tlsServer.shutdown();
}
```

### 2. **Cải thiện TlsServer.java**
```java
// Thêm method shutdown()
public void shutdown() {
    if (!running.get()) return;
    running.set(false);
    
    try {
        if (ss != null && !ss.isClosed()) {
            ss.close();
        }
        if (acceptThread != null && acceptThread.isAlive()) {
            acceptThread.join(5000);
        }
    } catch (Exception e) {
        logger.warning("Error during shutdown: " + e.getMessage());
    }
}

// Cải thiện error handling
public void start() throws Exception {
    try {
        running.set(true);
        acceptThread = new Thread(this::acceptLoop, "TLS-Accept-Thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    } catch (Exception e) {
        running.set(false);
        throw e;
    }
}
```

### 3. **Cập nhật SecurityConfig.java**
```java
// Tự động tạo keystore nếu không tồn tại
private void createKeystoreIfNeeded() {
    File keystoreFile = new File(keystorePath);
    if (!keystoreFile.exists()) {
        createSimpleKeystore();
    }
}

// Tạo keystore đơn giản với RSA key pair
private void createSimpleKeystore() throws Exception {
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(null, keystorePassword.toCharArray());
    
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048, new SecureRandom());
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    
    keystore.setKeyEntry("wifiguard", keyPair.getPrivate(), 
                        keystorePassword.toCharArray(), null);
    
    try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
        keystore.store(fos, keystorePassword.toCharArray());
    }
}
```

### 4. **Cập nhật server.properties**
```properties
# TLS/SSL Settings
tls.enabled=true
tls.port=9443
tls.keystore=server.jks
tls.keystorePassword=wifiguard123

# Server TLS Settings (for SecurityConfig compatibility)
server.tls.enabled=true
server.tls.keystore=server.jks
server.tls.password=wifiguard123
```

## **Cách test:**

### 1. **Chạy test TLS:**
```bash
cd server
mvn test -Dtest=TlsTest
```

### 2. **Chạy server với TLS:**
```bash
cd server
mvn exec:java -Dexec.mainClass="com.wifiguard.server.ServerMain"
```

### 3. **Kiểm tra log:**
- Server sẽ tự động tạo `server.jks` nếu không tồn tại
- TLS server sẽ start trên port 9443
- Cả TCP (9099) và TLS (9443) sẽ chạy song song

## **Kết quả mong đợi:**
- ✅ TLS Server khởi tạo thành công
- ✅ Keystore được tạo tự động
- ✅ Cả TCP và TLS server chạy song song
- ✅ Load thiết bị hoạt động bình thường qua cả hai protocol
- ✅ Shutdown graceful cho cả hai server

## **Lưu ý:**
- Keystore được tạo là self-signed, chỉ dùng cho test
- Trong production, nên sử dụng certificate từ CA uy tín
- Port 9443 có thể thay đổi trong `server.properties`
- TLS server sẽ tự động tạo keystore khi khởi động lần đầu

## **Troubleshooting:**
1. **Port đã được sử dụng:** Thay đổi port trong `server.properties`
2. **Keystore lỗi:** Xóa `server.jks` và restart server
3. **Permission denied:** Kiểm tra quyền ghi file trong thư mục server
4. **Memory issues:** Tăng heap size cho JVM nếu cần
