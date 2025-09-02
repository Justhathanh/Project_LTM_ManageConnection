package com.wifiguard.server;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class TlsServer {
    private static final Logger logger = Logger.getLogger(TlsServer.class.getName());
    
    private final int port;
    private final SSLServerSocket ss;
    private final DeviceMonitor monitor;
    private final Allowlist allow;
    private final AtomicBoolean running;
    private Thread acceptThread;
    
    public TlsServer(int port, SecurityConfig sec, DeviceMonitor monitor, Allowlist allow) throws Exception {
        this.port = port;
        this.monitor = monitor;
        this.allow = allow;
        this.running = new AtomicBoolean(false);
        
        SSLServerSocketFactory fac = sec.buildSSLContext().getServerSocketFactory();
        this.ss = (SSLServerSocket) fac.createServerSocket(port);
        
        // Configure TLS protocols
        ss.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        
        logger.info("TLS Server initialized on port " + port);
    }
    
    // Thêm constructor mới để khớp với cách gọi từ ServerMain
    public TlsServer(int port, SecurityConfig sec, DeviceMonitor monitor, Allowlist allow, ServerMain serverMain) throws Exception {
        this(port, sec, monitor, allow); // Gọi constructor chính
    }
    
    public void start() throws Exception {
        if (running.get()) {
            logger.warning("TLS Server is already running");
            return;
        }
        
        try {
            running.set(true);
            acceptThread = new Thread(this::acceptLoop, "TLS-Accept-Thread");
            acceptThread.setDaemon(true);
            acceptThread.start();
            
            logger.info("[TLS] WiFiGuard server listening on port " + port);
        } catch (Exception e) {
            running.set(false);
            logger.log(java.util.logging.Level.SEVERE, "Failed to start TLS server", e);
            throw e;
        }
    }
    
    private void acceptLoop() {
        logger.info("TLS Accept thread started");
        
        while (running.get() && !ss.isClosed()) {
            try {
                Socket s = ss.accept();
                new Thread(new ClientHandler(s, allow, monitor, null)).start();
            } catch (Exception e) {
                if (running.get()) {
                    logger.log(java.util.logging.Level.WARNING, "Error accepting TLS client connection", e);
                }
            }
        }
        
        logger.info("TLS Accept thread finished");
    }
    
    public void shutdown() {
        if (!running.get()) {
            logger.info("TLS Server is not running");
            return;
        }
        
        running.set(false);
        
        try {
            if (ss != null && !ss.isClosed()) {
                ss.close();
                logger.info("TLS Server socket closed");
            }
            
            if (acceptThread != null && acceptThread.isAlive()) {
                acceptThread.join(5000); // Wait up to 5 seconds
                if (acceptThread.isAlive()) {
                    logger.warning("TLS Accept thread did not terminate gracefully");
                }
            }
            
            logger.info("TLS Server shutdown completed");
        } catch (Exception e) {
            logger.log(java.util.logging.Level.WARNING, "Error during TLS Server shutdown", e);
        }
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public int getPort() {
        return port;
    }
}
