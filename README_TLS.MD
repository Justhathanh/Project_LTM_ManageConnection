WiFiGuard — TLS Quickstart (feature/tls-security)
1) Yêu cầu

JDK 17+ (đã có sẵn).

Không cần Maven (build thủ công bằng javac).

2) Build (VS Code Terminal/PowerShell)

Tại thư mục gốc WifiGuard/:

mkdir bin -Force | Out-Null
Get-ChildItem -Recurse -Filter *.java | % { $_.FullName } > sources.txt
javac -encoding UTF-8 -d bin (Get-Content sources.txt)


Copy cấu hình:

copy server\src\main\resources\server.properties bin\
copy server\src\main\resources\allowlist.txt bin\

3) Tạo keystore & bật TLS (Server)

Trong thư mục bin/:

keytool -genkeypair -alias wifiguard -keyalg RSA -keysize 2048 `
  -storetype PKCS12 -keystore keystore.p12 -validity 365 `
  -storepass <PASSWORD>


Mở bin/server.properties:

server.port=9099
router.mode=dummy         # (v1 test; gateway thật sẽ ở nhánh khác)
server.tls.enabled=true
server.tls.keystore=keystore.p12
server.tls.password=<PASSWORD>


Chạy server (TLS):

java com.wifiguard.server.ServerMain

4) Chạy Client (TLS)

Vì cert tự ký, client cần truststore. Cách nhanh nhất (PowerShell):

java --% -Djavax.net.ssl.trustStore=keystore.p12 -Djavax.net.ssl.trustStorePassword=<PASSWORD> -Djavax.net.ssl.trustStoreType=PKCS12 com.wifiguard.client.ClientMain 127.0.0.1 9099 tls


CMD (cmd.exe) cũng chạy được cùng tham số, không cần --%.

5) Kiểm tra trên Wireshark

Mở Wireshark → Adapter for loopback traffic capture (vì dùng 127.0.0.1).

Filter: tcp.port == 9099.

Kết nối lại client → thấy chuỗi Client Hello, Server Hello, Certificate → sau đó toàn bộ Application Data (Encrypted) (không còn text LIST/ADD/...).

6) Dùng TCP thường (tắt TLS) — để so sánh nhanh

bin/server.properties:

server.tls.enabled=false


Chạy lại server:

java com.wifiguard.server.ServerMain


Client (TCP):

java com.wifiguard.client.ClientMain 127.0.0.1 9099


Wireshark sẽ thấy payload là plain text (so sánh với TLS).

7) Lệnh CLI hay dùng (gõ tại Client)
LIST
ADD aa:bb:cc:11:22:33
DEL aa:bb:cc:11:22:33
STATUS
QUIT


allowlist.txt rỗng ⇒ mọi thiết bị đều UNKNOWN. Thêm MAC vào allowlist để thành KNOWN.

8) Troubleshooting nhanh

FileNotFoundException keystore.p12: đặt đúng file trong bin/ hoặc chỉnh server.tls.keystore thành đường dẫn đúng.

PKIX path building failed: client chưa trust cert tự ký → chạy client kèm -Djavax.net.ssl.trustStore=... như mục 4.

Wireshark không thấy gói: chọn đúng Loopback Adapter (không phải Wi-Fi) khi dùng 127.0.0.1; hoặc bind server ra IP LAN nếu muốn bắt trên Wi-Fi.