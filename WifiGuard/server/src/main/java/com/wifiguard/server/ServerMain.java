package com.wifiguard.server;

import com.wifiguard.server.gateway.RouterGateway;
import com.wifiguard.server.gateway.DummyRouterGateway;
import java.io.File;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        ServerConfig cfg = new ServerConfig();
        SecurityConfig sec = new SecurityConfig(cfg);

        Allowlist allow = new Allowlist(new File("allowlist.txt"));

        RouterGateway gw = new DummyRouterGateway();

        DeviceMonitor monitor = new DeviceMonitor(gw, allow, cfg.pollSeconds, cfg.banSeconds);
        Thread mon = new Thread(monitor, "device-monitor");
        mon.setDaemon(true);
        mon.start();

        if (sec.tlsEnabled) {
            new TlsServer(cfg.port, sec, monitor, allow).start();
        } else {
            new TcpServer(cfg.port, monitor, allow).start();
        }
    }
}
