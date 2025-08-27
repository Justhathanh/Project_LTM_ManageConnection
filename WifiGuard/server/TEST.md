# WifiGuard Server Test Guide

## 🚀 Quick Start

### Bước 1: Build và Start Server
```bash
cd server
mvn clean compile
mvn test-compile
java -cp "target/classes" com.wifiguard.server.ServerMain
```

Script này sẽ:
1. Build server với Maven
2. Khởi động server trên port 9099
3. Hiển thị hướng dẫn test

### Bước 2: Test với Client
Mở terminal mới và kết nối đến server:

#### Sử dụng Java TestClient (Khuyến nghị)
```bash
cd server
java -cp "target/classes;target/test-classes" TestClient
```

#### Hoặc sử dụng bất kỳ TCP client nào
Kết nối đến `localhost:9099`

## 📋 Test Commands

| Command | Mô tả | Ví dụ |
|---------|-------|-------|
| `LIST` | Liệt kê tất cả thiết bị | `LIST` |
| `STATUS` | Trạng thái server | `STATUS` |
| `ADD` | Thêm thiết bị vào allowlist | `ADD 00:1B:44:11:22:33 MyPhone 192.168.1.100` |
| `DEL` | Xóa thiết bị khỏi allowlist | `DEL 00:1B:44:11:22:33` |
| `QUIT` | Thoát kết nối | `QUIT` |

## 🔄 Test Workflow

```
1. Build và start server: mvn clean compile && java -cp "target/classes" com.wifiguard.server.ServerMain
2. Mở terminal mới
3. Kết nối: java -cp "target/classes;target/test-classes" TestClient
4. Test commands:
   LIST
   STATUS
   ADD 00:1B:44:11:22:33 TestDevice 192.168.1.100
   LIST
   DEL 00:1B:44:11:22:33
   LIST
   QUIT
```

## ⚠️ Troubleshooting

### Server không khởi động
- Kiểm tra Java 17+ và Maven đã cài đặt
- Kiểm tra port 9099 có bị chiếm không

### Không kết nối được
- Đảm bảo server đang chạy
- Kiểm tra firewall
- Thử port khác trong `server.properties`

### TestClient không chạy
- Đảm bảo đã chạy `mvn test-compile`
- Kiểm tra classpath có đúng không

## 🎯 Tips

1. **Luôn build trước** khi test: `mvn clean compile`
2. **Compile test classes**: `mvn test-compile`
3. **Sử dụng 2 terminal** - một cho server, một cho client
4. **Kiểm tra logs** để debug
5. **Test từng command** một cách tuần tự

## 🔧 Alternative Testing Methods

### **1. Java TestClient (Recommended)**
```bash
java -cp "target/classes;target/test-classes" TestClient
```

### **2. GUI TCP Clients**
- **PuTTY** (Free): Connection type: Raw, Host: localhost, Port: 9099
- **MobaXterm** (Free): Raw connection to localhost:9099
- **SecureCRT**: Raw connection to localhost:9099

### **3. Command Line Tools**
- **Netcat**: `nc localhost 9099`
- **PowerShell**: `Test-NetConnection -ComputerName localhost -Port 9099`
- **Online tools**: https://www.tcpclient.com/

### **4. Custom Client**
Bạn có thể viết client đơn giản bằng bất kỳ ngôn ngữ nào:
- **Python**: `socket` module
- **Node.js**: `net` module  
- **C#**: `TcpClient` class
- **Java**: `Socket` class

## ✨ Clean Code Improvements

### 🏗️ **Architecture**
- **Dependency Injection**: Loại bỏ static methods, sử dụng constructor injection
- **Single Responsibility**: Mỗi class có một trách nhiệm rõ ràng
- **Separation of Concerns**: Tách biệt logic xử lý và data models

### 🔧 **Code Quality**
- **Immutable Objects**: DeviceInfo và Response sử dụng final fields
- **Constants**: Sử dụng constants thay vì magic numbers
- **Error Handling**: Better exception handling và logging
- **Resource Management**: Proper cleanup và resource management

### 📚 **Documentation**
- **Clear Comments**: Mô tả rõ ràng cho mỗi method
- **JavaDoc Style**: Consistent documentation format
- **English Naming**: Sử dụng tiếng Anh cho tên methods và variables

### 🚀 **Performance**
- **ConcurrentHashMap**: Thread-safe collections
- **ExecutorService**: Proper thread management
- **Connection Pooling**: Efficient client handling
- **Timeout Handling**: Socket timeouts và connection limits
