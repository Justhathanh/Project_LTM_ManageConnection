Structure
wifiguard/
├─ pom.xml                       # parent (multi-module)
├─ common/
│  ├─ pom.xml
│  └─ src/main/java/com/wifiguard/common/
│     ├─ model/
│     │  ├─ Device.java         # MAC, RSSI, rate, vendor
│     │  └─ GatewayInfo.java    # ip, kind, auth
│     ├─ proto/
│     │  ├─ Command.java        # ADD/DEL/LIST/BLOCK/UNBLOCK/SCAN/SELECT/STATUS
│     │  └─ Message.java        # request/response/event
│     └─ util/
│        ├─ MacUtil.java
│        └─ JsonUtil.java
├─ server/
│  ├─ pom.xml
│  └─ src/main/java/com/wifiguard/server/
│     ├─ ServerMain.java
│     ├─ ServerConfig.java
│     ├─ auth/
│     │  └─ TokenAuth.java      # optional token for clients
│     ├─ core/
│     │  ├─ Allowlist.java
│     │  ├─ DeviceMonitor.java  # poll → detect → notify
│     │  └─ PolicyEngine.java   # allowlist-only/monitor/quarantine
│     ├─ discovery/
│     │  ├─ DiscoveryService.java   # orchestrator
│     │  ├─ ArpScanner.java         # find gateways in LAN
│     │  ├─ MdnsScanner.java        # _ubus._tcp, _http._tcp
│     │  └─ SsdpScanner.java        # UPnP routers
│     ├─ gateway/
│     │  ├─ RouterGateway.java      # interface: list(), block()
│     │  ├─ DummyGateway.java
│     │  ├─ OpenWrtGateway.java     # uBUS get_clients/del_client
│     │  └─ (stubs) MikrotikGateway.java / UnifiGateway.java
│     ├─ notify/
│     │  ├─ Notifier.java
│     │  ├─ ConsoleNotifier.java
│     │  └─ TelegramNotifier.java   # optional
│     └─ tcp/
│        ├─ ServerAcceptor.java     # Socket listener (multi-client)
│        └─ ClientSession.java      # parse commands, stream events
│  └─ src/main/resources/
│     ├─ server.properties      # port, pollSeconds, router.mode, auth token…
│     └─ allowlist.txt
├─ client/
│  ├─ pom.xml
│  └─ src/main/java/com/wifiguard/client/
│     ├─ ClientMain.java        # CLI
│     ├─ ClientConfig.java
│     └─ tcp/ClientConnection.java
│  └─ src/main/java/com/wifiguard/client/ui/ (later)
│     ├─ App.java               # JavaFX main (future)
│     └─ DevicesController.java
└─ docs/
   ├─ PROTOCOL.md               # command grammar + events
   └─ FLOWS.md                  # 1) discover → 2) list → 3) block/notify
