# WifiGuard — Multi‑Client Server (Windows Hotspot Monitor)

## Mục tiêu

* Hiển thị danh sách thiết bị đang nối vào **hotspot điện thoại** khi **laptop Windows** kết nối SSID đó.
* Phân loại **KNOWN/UNKNOWN** theo `allowlist.txt`.
* Kênh client↔server hỗ trợ **TLS** (tùy chọn) để che mọi lệnh/sự kiện trên Wireshark.

---

## Cấu trúc dự án

```
WifiGuard/
├─ client/src/main/java/com/wifiguard/client/
│   └─ ClientMain.java
├─ server/src/main/java/com/wifiguard/server/
│   ├─ ServerMain.java
│   ├─ DeviceMonitor.java
│   ├─ Allowlist.java
│   ├─ ClientHandler.java
│   ├─ SecurityConfig.java
│   ├─ TcpServer.java
│   ├─ TlsServer.java
│   ├─ gateway/
│   │   ├─ RouterGateway.java
│   │   ├─ WindowsArpGateway.java
│   │   ├─ DummyRouterGateway.java
│   │   └─ OpenWrtRouterGateway.java
│   └─ notify/
│       └─ ConsoleNotifier.java
├─ server/src/main/resources/
│   ├─ allowlist.txt
│   └─ server.properties
├─ pom.xml
└─ README.md

```

---

## Nhánh (branch) & phân công

| Branch                 | Người phụ trách | Phạm vi                                                                  | Điểm hoàn thành                                                 |
| ---------------------- | --------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `feature/core-server`  | A               | `ServerMain`, `DeviceMonitor`, `Allowlist`, `ClientHandler`, `TcpServer` | Lệnh `LIST/ADD/DEL/STATUS/QUIT`; emit `EVENT CONNECTED/UNKNOWN` |
| `feature/gateway`      | B               | `RouterGateway`, `WindowsArpGateway`, mock `DummyRouterGateway`          | `getConnectedDevices()` từ `arp -a`, lọc gateway/broadcast      |
| `feature/tls-security` | C               | `SecurityConfig`, `TlsServer`, cập nhật `ServerMain`, `ClientMain`       | Bật TLS; Wireshark chỉ thấy TLS handshake + encrypted data      |
| `feature/client-cli`   | D               | `ClientMain`, `notify/ConsoleNotifier`                                   | CLI realtime, highlight UNKNOWN, thao tác nhanh `ADD`           |

### Tạo branch

```bash
git checkout -b feature/core-server && git push -u origin feature/core-server
# tương tự cho: feature/gateway, feature/tls-security, feature/client-cli
```

### Quy trình PR

1. Commit theo nhóm chức năng, message rõ ràng.
2. Mở Pull Request vào `main`, gắn reviewer chéo.
3. CI: build Maven + lint (nếu có). Fix trước khi merge.
4. Squash & merge; xóa branch sau khi merge.

---

## Build & Run

### Toàn bộ modules

```bash
mvn -q clean package
```

### Chạy Server (Windows ARP mode)

`server/src/main/resources/server.properties` ví dụ:

```properties
server.port=9099
router.mode=windowsarp
monitor.pollSeconds=5
monitor.banSeconds=0

# TLS (tùy chọn)
server.tls.enabled=true
server.tls.keystore=keystore.p12
server.tls.password=changeit
```

Chạy:

```bash
java -jar server/target/wifiguard-server-1.0.0-jar-with-dependencies.jar
```

### Chạy Client

```bash
# TCP
java -jar client/target/wifiguard-client-1.0.0-jar-with-dependencies.jar 127.0.0.1 9099
# TLS
java -jar client/target/wifiguard-client-1.0.0-jar-with-dependencies.jar 127.0.0.1 9099 tls
```

---

## TLS Hướng dẫn nhanh

Tạo keystore tự ký:

```bash
keytool -genkeypair -alias wifiguard -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 365 -storepass changeit
```

Bật TLS trong `server.properties` như trên. Khi capture bằng Wireshark: thấy `ClientHello/ServerHello/Certificate`, payload là *Encrypted Application Data*.

---

## Logic “UNKNOWN”

* App lấy danh sách MAC/IP từ **ARP** (Windows) hoặc API router.
* Nếu MAC **không** nằm trong `allowlist.txt` ⇒ gắn nhãn **UNKNOWN** và phát `EVENT UNKNOWN <mac>`.

---

## Lệnh CLI

* `LIST` — in allowlist
* `ADD <mac>` / `DEL <mac>` — cập nhật allowlist
* `STATUS` — thông tin cơ bản
* `QUIT` — thoát

---

## Lưu ý triển khai

* Laptop **phải kết nối** vào SSID hotspot của điện thoại.
* ARP chỉ hiển thị thiết bị đang hoạt động; ping‑sweep nhẹ để cập nhật bảng ARP.
* Block thiết bị lạ thực hiện **thủ công trên điện thoại** (v1 không block tự động).

---

## Quy ước code

* Java 17, Maven.
* Không để secret trong repo; `server.properties` đọc từ resources.
* Log sự kiện: CONNECTED/UNKNOWN/ERROR kèm timestamp.

---

## License

MIT (cập nhật theo nhu cầu).
