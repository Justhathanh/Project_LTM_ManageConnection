package com.wifiguard.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * TCP Server for accepting client connections with optional TLS support
 */
public class TcpServer {
    private static final Logger logger = Logger.getLogger(TcpServer.class.getName());
    private static final int THREAD_JOIN_TIMEOUT = 5000; // 5 seconds
    private static final int DEFAULT_BACKLOG = 50;
    
    private final int port;
    private final String host;
    private final int backlog;
    private final boolean tlsEnabled;
    private final ExecutorService clientExecutor;
    private final AtomicBoolean running;
    private final AtomicInteger connectionCounter;
    private final Allowlist allowlist;
    private final DeviceMonitor deviceMonitor;
    private final ServerMain serverMain;
    
    private ServerSocket serverSocket;
    private Thread acceptThread;
    
    public TcpServer(int port, Allowlist allowlist, DeviceMonitor deviceMonitor, ServerMain serverMain) {
        this.port = port;
        this.host = "0.0.0.0"; // Default to all interfaces
        this.backlog = DEFAULT_BACKLOG;
        this.tlsEnabled = false; // Default to non-TLS
        this.allowlist = allowlist;
        this.deviceMonitor = deviceMonitor;
        this.serverMain = serverMain;
        this.clientExecutor = createThreadPool();
        this.running = new AtomicBoolean(false);
        this.connectionCounter = new AtomicInteger(0);
    }
    
    public TcpServer(Properties config, Allowlist allowlist, DeviceMonitor deviceMonitor, ServerMain serverMain) {
        this.port = Integer.parseInt(config.getProperty("server.port", "9099"));
        this.host = config.getProperty("server.host", "0.0.0.0");
        this.backlog = Integer.parseInt(config.getProperty("server.backlog", String.valueOf(DEFAULT_BACKLOG)));
        this.tlsEnabled = Boolean.parseBoolean(config.getProperty("tls.enabled", "false"));
        this.allowlist = allowlist;
        this.deviceMonitor = deviceMonitor;
        this.serverMain = serverMain;
        this.clientExecutor = createThreadPool(config);
        this.running = new AtomicBoolean(false);
        this.connectionCounter = new AtomicInteger(0);
    }
    
    /**
     * Create thread pool with configuration
     */
    private ExecutorService createThreadPool() {
        return createThreadPool(null);
    }
    
    private ExecutorService createThreadPool(Properties config) {
        int poolSize = config != null ? 
            Integer.parseInt(config.getProperty("server.threadPool.size", "20")) : 20;
        
        return Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private final AtomicInteger threadCounter = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "Client-Handler-" + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }
    
    /**
     * Start the server
     */
    public void start() {
        if (running.get()) {
            logger.warning("Server is already running");
            return;
        }
        
        try {
            createServerSocket();
            running.set(true);
            
            acceptThread = new Thread(this::acceptLoop, "Server-Accept-Thread");
            acceptThread.setDaemon(true);
            acceptThread.start();
            
            logger.info("TCP Server started on " + host + ":" + port + (tlsEnabled ? " (TLS)" : ""));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start server on " + host + ":" + port, e);
            throw new RuntimeException("Failed to start server", e);
        }
    }
    
    /**
     * Create server socket (TLS or regular)
     */
    private void createServerSocket() throws IOException {
        if (tlsEnabled) {
            createTLSServerSocket();
        } else {
            createRegularServerSocket();
        }
    }
    
    /**
     * Create TLS server socket
     */
    private void createTLSServerSocket() throws IOException {
        try {
            SSLContext sslContext = SSLContext.getDefault();
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            SSLServerSocket sslSocket = (SSLServerSocket) factory.createServerSocket();
            
            sslSocket.setReuseAddress(true);
            sslSocket.bind(new InetSocketAddress(host, port), backlog);
            
            // Configure TLS protocols and cipher suites
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            
            this.serverSocket = sslSocket;
            logger.info("TLS Server socket created successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create TLS server socket", e);
            throw new IOException("TLS initialization failed", e);
        }
    }
    
    /**
     * Create regular server socket
     */
    private void createRegularServerSocket() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port), backlog);
        logger.info("Regular Server socket created successfully");
    }
    
    /**
     * Stop the server
     */
    public void stop() {
        if (!running.get()) {
            logger.warning("Server is not running");
            return;
        }
        
        running.set(false);
        closeServerSocket();
        waitForAcceptThread();
        shutdownClientExecutor();
        
        logger.info("TCP Server stopped");
    }
    
    /**
     * Close server socket
     */
    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logger.info("Server socket closed");
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing server socket", e);
            }
        }
    }
    
    /**
     * Wait for accept thread to finish
     */
    private void waitForAcceptThread() {
        if (acceptThread != null && acceptThread.isAlive()) {
            try {
                acceptThread.join(THREAD_JOIN_TIMEOUT);
                if (acceptThread.isAlive()) {
                    logger.warning("Accept thread did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, "Interrupted while waiting for accept thread", e);
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Shutdown client executor
     */
    private void shutdownClientExecutor() {
        if (!clientExecutor.isShutdown()) {
            clientExecutor.shutdown();
        }
    }
    
    /**
     * Main accept loop
     */
    private void acceptLoop() {
        logger.info("Accept thread started");
        
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleNewClient(clientSocket);
            } catch (IOException e) {
                if (running.get()) {
                    logger.log(Level.WARNING, "Error accepting client connection", e);
                }
            }
        }
        
        logger.info("Accept thread finished");
    }
    
    /**
     * Handle new client connection
     */
    private void handleNewClient(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        int connectionId = connectionCounter.incrementAndGet();
        
        logger.info("Client connected: " + clientAddress + " (ID: " + connectionId + ")");
        
        ClientHandler clientHandler = new ClientHandler(clientSocket, allowlist, deviceMonitor, serverMain);
        clientExecutor.submit(clientHandler);
    }
    
    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Get server port
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Get server host
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Check if TLS is enabled
     */
    public boolean isTlsEnabled() {
        return tlsEnabled;
    }
    
    /**
     * Get active connection count
     */
    public int getActiveConnectionCount() {
        return connectionCounter.get();
    }
    
    /**
     * Shutdown server
     */
    public void shutdown() {
        stop();
        
        if (!clientExecutor.isTerminated()) {
            clientExecutor.shutdownNow();
        }
        
        logger.info("TCP Server shutdown completed");
    }
}
