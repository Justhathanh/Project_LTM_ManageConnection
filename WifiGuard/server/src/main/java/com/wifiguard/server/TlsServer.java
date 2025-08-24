package com.wifiguard.server;
import javax.net.ssl.*; import java.net.Socket;
public class TlsServer {
  private final int port; private final SSLServerSocket ss; private final DeviceMonitor monitor; private final Allowlist allow;
  public TlsServer(int port, SecurityConfig sec, DeviceMonitor monitor, Allowlist allow) throws Exception {
    this.port=port; this.monitor=monitor; this.allow=allow;
    SSLServerSocketFactory fac = sec.buildSSLContext().getServerSocketFactory();
    this.ss = (SSLServerSocket) fac.createServerSocket(port);
  }
  public void start() throws Exception {
    System.out.println("[TLS] WiFiGuard server listening on port " + port);
    while (true) { Socket s = ss.accept(); new Thread(new ClientHandler(s, allow, monitor, null)).start(); }
  }
}
