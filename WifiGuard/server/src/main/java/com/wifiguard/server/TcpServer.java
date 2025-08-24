package com.wifiguard.server;
import java.net.*; 
public class TcpServer {
  private final int port; private final DeviceMonitor monitor; private final Allowlist allow;
  public TcpServer(int port, DeviceMonitor monitor, Allowlist allow){ this.port=port; this.monitor=monitor; this.allow=allow; }
  public void start() throws Exception {
    try (ServerSocket ss = new ServerSocket(port)) {
      System.out.println("[TCP] WiFiGuard server listening on port " + port);
      while (true) { Socket s = ss.accept(); new Thread(new ClientHandler(s, allow, monitor)).start(); }
    }
  }
}
