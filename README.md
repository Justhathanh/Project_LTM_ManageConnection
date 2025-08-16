wifiguard/
├─ pom.xml
├─ server/
│  └─ src/main/java/com/wifiguard/server/
│     ├─ ServerMain.java
│     ├─ ServerConfig.java
│     ├─ SecurityConfig.java        # NEW: cấu hình TLS
│     ├─ TcpServer.java             # NEW: server TCP thuần (fallback)
│     ├─ TlsServer.java             # NEW: server TLS (SSLServerSocket)
│     ├─ Allowlist.java
│     ├─ DeviceMonitor.java
│     ├─ ClientHandler.java
│     ├─ gateway/
│     │  ├─ RouterGateway.java
│     │  ├─ DummyRouterGateway.java
│     │  ├─ OpenWrtRouterGateway.java
│     │  └─ WindowsArpGateway.java  # NEW: lấy danh sách client qua ARP (Windows hotspot)
│     ├─ notify/
│     │  └─ ConsoleNotifier.java
│     └─ tcp/
│        └─ (không cần nữa nếu dùng TcpServer trực tiếp, nhưng có thể giữ)
│
│  └─ src/main/resources/
│     ├─ server.properties          # cấu hình chung (TLS, router mode, poll…)
│     └─ allowlist.txt              # danh sách MAC được phép
│
└─ client/
   └─ src/main/java/com/wifiguard/client/
      ├─ ClientMain.java            # CLI, hỗ trợ TCP hoặc TLS
      └─ TcpClient.java (optional)  # có thể gộp luôn vào ClientMain
