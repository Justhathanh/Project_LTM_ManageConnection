# Khắc phục vấn đề TLS trong WifiGuard

## **Vấn đề đã được xác định:**

### 1. **Cấu hình không khớp**
- `server.properties` sử dụng `tls.enabled=true`
- `SecurityConfig.java` tìm kiếm `server.tls.enabled`
- `ServerMain.java` tìm kiếm `tls.enabled`

### 2. **Thiếu keystore**
- Không có file `server.jks` để xác thực SSL/TLS
- Server không thể khởi tạo TLS context

### 3. **TlsServer không được khởi tạo đúng cách**
- Logic khởi tạo có vấn đề với cấu hình

## **Giải pháp đã thực hiện:**

### 1. **Sửa SecurityConfig.java**
```java
// Sửa key cấu hình để khớp với server.properties
this.tlsEnabled = Boolean.parseBoolean(cfg.props.getProperty("tls.enabled", "false"));
this.keystorePath = cfg.props.getProperty("tls.keystore", "server.jks");
this.keystorePassword = cfg.props.getProperty("tls.keystorePassword", "wifiguard123");
```

### 2. **Sửa ServerMain.java**
```java
// Đảm bảo ServerConfig sử dụng cùng cấu hình
ServerConfig serverConfig = new ServerConfig();
serverConfig.props.putAll(this.config);
SecurityConfig securityConfig = new SecurityConfig(serverConfig);
```

### 3. **Đơn giản hóa keystore creation**
- Loại bỏ certificate phức tạp
- Chỉ tạo RSA key pair đơn giản
- Tự động tạo keystore khi cần

## **Cách test:**

### 1. **Compile và chạy test:**
```bash
cd server
mvn compile
java -cp "target/classes" com.wifiguard.server.TlsTest
```

### 2. **Hoặc sử dụng script:**
```bash
# Windows
test_tls.bat

# PowerShell
.\test_tls.ps1
```

### 3. **Chạy server với TLS:**
```bash
cd server
mvn exec:java -Dexec.mainClass="com.wifiguard.server.ServerMain"
```

## **Kết quả mong đợi:**

- ✅ TLS Server khởi tạo thành công
- ✅ Keystore `server.jks` được tạo tự động
- ✅ SSL Context được tạo thành công
- ✅ Cả TCP (9099) và TLS (9443) server chạy song song
- ✅ Load thiết bị hoạt động bình thường qua cả hai protocol

## **Troubleshooting:**

### 1. **Port đã được sử dụng:**
- Thay đổi port trong `server.properties`
- Kiểm tra process đang sử dụng port

### 2. **Keystore lỗi:**
- Xóa `server.jks` và restart server
- Server sẽ tự động tạo keystore mới

### 3. **Permission denied:**
- Kiểm tra quyền ghi file trong thư mục server
- Chạy với quyền administrator nếu cần

### 4. **Memory issues:**
- Tăng heap size cho JVM: `-Xmx512m`

## **Cấu hình cuối cùng:**

```properties
# TLS/SSL Settings
tls.enabled=true
tls.port=9443
tls.keystore=server.jks
tls.keystorePassword=wifiguard123

# Server TLS Settings (for compatibility)
server.tls.enabled=true
server.tls.keystore=server.jks
server.tls.password=wifiguard123
```

## **Lưu ý:**

- Keystore được tạo là self-signed, chỉ dùng cho test
- Trong production, nên sử dụng certificate từ CA uy tín
- Port 9443 có thể thay đổi trong `server.properties`
- TLS server sẽ tự động tạo keystore khi khởi động lần đầu
