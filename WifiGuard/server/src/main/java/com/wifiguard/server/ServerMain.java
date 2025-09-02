package com.wifiguard.server;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Main entry point for WifiGuard Server
 * Manages server lifecycle, component initialization, and health monitoring
 */
public class ServerMain {
    private static final Logger logger = Logger.getLogger(ServerMain.class.getName());
    
    // Server components
    private final Allowlist allowlist;
    private final DeviceMonitor deviceMonitor;
    private final TcpServer tcpServer;
    private final TlsServer tlsServer; // Thêm TlsServer
    
    // Performance metrics
    private final AtomicInteger activeConnections;
    private final AtomicLong totalConnections;
    private final AtomicLong totalCommands;
    private final AtomicLong startTime;
    
    // Server state
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isShuttingDown;
    
    // Configuration
    private final Properties config;
    
    public ServerMain() {
        try {
            logger.info("Đang khởi tạo ServerMain...");
            
            this.config = loadConfiguration();
            logger.info("Configuration đã được load");
            
            this.allowlist = new Allowlist();
            logger.info("Allowlist đã được khởi tạo");
            
            this.deviceMonitor = new DeviceMonitor(allowlist, config);
            logger.info("DeviceMonitor đã được khởi tạo");
            
            this.tcpServer = new TcpServer(config, allowlist, deviceMonitor, this);
            logger.info("TcpServer đã được khởi tạo");
            
            // Khởi tạo TlsServer nếu TLS được bật
            if (isTlsEnabled()) {
                try {
                    ServerConfig serverConfig = new ServerConfig();
                    // Cập nhật props trong ServerConfig với config từ ServerMain
                    serverConfig.props.putAll(this.config);
                    SecurityConfig securityConfig = new SecurityConfig(serverConfig);
                    this.tlsServer = new TlsServer(getTlsPort(), securityConfig, deviceMonitor, allowlist, this);
                    logger.info("TlsServer đã được khởi tạo");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Không thể khởi tạo TlsServer", e);
                    throw new RuntimeException("TLS initialization failed", e);
                }
            } else {
                this.tlsServer = null;
                logger.info("TLS không được bật");
            }
            
            // Initialize metrics
            this.activeConnections = new AtomicInteger(0);
            this.totalConnections = new AtomicLong(0);
            this.totalCommands = new AtomicLong(0);
            this.startTime = new AtomicLong(System.currentTimeMillis());
            
            // Initialize state
            this.isRunning = new AtomicBoolean(false);
            this.isShuttingDown = new AtomicBoolean(false);
            
            logger.info("ServerMain khởi tạo thành công");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Lỗi khởi tạo ServerMain", e);
            throw new RuntimeException("Không thể khởi tạo ServerMain", e);
        }
    }
    
    public static void main(String[] args) {
        try {
            ServerMain server = new ServerMain();
            server.start();
            server.waitForShutdown();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start server", e);
            System.exit(1);
        }
    }
    
    /**
     * Start the server
     */
    public void start() {
        if (isRunning.get()) {
            logger.warning("Server is already running");
            return;
        }
        
        logger.info("Starting WifiGuard Server...");
        
        try {
            setupLogging();
            logConfiguration();
            validateConfiguration();
            initializeComponents();
            startComponents();
            addShutdownHook();
            
            isRunning.set(true);
            logger.info("Server started successfully on port " + getServerPort());
            logServerInfo();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start server", e);
            throw new RuntimeException("Server startup failed", e);
        }
    }
    
    /**
     * Setup logging configuration
     */
    private void setupLogging() {
        try {
            LogManager.getLogManager().readConfiguration();
        } catch (IOException e) {
            // Use default logging if config not found
            System.setProperty("java.util.logging.SimpleFormatter.format", 
                "[%1$tF %1$tT] [%4$-7s] %2$s: %5$s%6$s%n");
        }
        logger.info("Logging initialized");
    }
    
    /**
     * Load configuration from file or use defaults
     */
    private Properties loadConfiguration() {
        Properties config = new Properties();
        
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("server.properties")) {
            if (input != null) {
                config.load(input);
                logger.info("Configuration loaded from server.properties");
            } else {
                logger.warning("server.properties not found, using defaults");
                loadDefaultConfig(config);
            }
        } catch (IOException e) {
            logger.warning("Error loading configuration, using defaults: " + e.getMessage());
            loadDefaultConfig(config);
        }
        
        return config;
    }
    
    /**
     * Validate configuration values
     */
    private void validateConfiguration() {
        try {
            int port = getServerPort();
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid port number: " + port);
            }
            
            int threadPoolSize = getThreadPoolSize();
            if (threadPoolSize < 1 || threadPoolSize > 1000) {
                throw new IllegalArgumentException("Invalid thread pool size: " + threadPoolSize);
            }
            
            int pollInterval = getPollInterval();
            if (pollInterval < 1 || pollInterval > 3600) {
                throw new IllegalArgumentException("Invalid poll interval: " + pollInterval);
            }
            
            logger.info("Configuration validation passed");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Configuration validation failed", e);
            throw new RuntimeException("Invalid configuration", e);
        }
    }
    
    /**
     * Load default configuration values
     */
    private void loadDefaultConfig(Properties config) {
        // Server settings
        config.setProperty("server.port", "9099");
        config.setProperty("server.host", "0.0.0.0");
        config.setProperty("server.backlog", "50");
        config.setProperty("server.threadPool.size", "20");
        config.setProperty("server.connectionTimeout", "30000");
        config.setProperty("server.readTimeout", "10000");
        
        // TLS settings
        config.setProperty("tls.enabled", "false");
        config.setProperty("tls.keystore", "server.jks");
        config.setProperty("tls.keystorePassword", "wifiguard123");
        
        // Device monitoring
        config.setProperty("monitor.pollSeconds", "5");
        config.setProperty("monitor.banSeconds", "600");
        config.setProperty("monitor.networkScanRange", "1000"); // Tăng từ 254 lên 1000 để quét nhiều thiết bị hơn
        config.setProperty("monitor.pingTimeout", "500"); // Giảm timeout để quét nhanh hơn
        
        // Router integration
        config.setProperty("router.mode", "windowsarp");
        config.setProperty("router.openwrt.host", "192.168.1.1");
        config.setProperty("router.openwrt.username", "admin");
        config.setProperty("router.openwrt.password", "");
        
        // Logging
        config.setProperty("logging.level", "INFO");
        config.setProperty("logging.file.enabled", "true");
        config.setProperty("logging.console.enabled", "true");
    }
    
    /**
     * Log configuration details
     */
    private void logConfiguration() {
        logger.info("=== Server Configuration ===");
        logger.info("Server Host: " + getServerHost());
        logger.info("Server Port: " + getServerPort());
        logger.info("Server Backlog: " + getServerBacklog());
        logger.info("TLS Enabled: " + isTlsEnabled());
        logger.info("Thread Pool Size: " + getThreadPoolSize());
        logger.info("Poll Interval: " + getPollInterval() + " seconds");
        logger.info("Router Mode: " + getRouterMode());
        logger.info("=============================");
    }
    
    /**
     * Log server information
     */
    private void logServerInfo() {
        logger.info("=== Server Information ===");
        logger.info("Java Version: " + System.getProperty("java.version"));
        logger.info("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        logger.info("Architecture: " + System.getProperty("os.arch"));
        logger.info("Available Processors: " + Runtime.getRuntime().availableProcessors());
        logger.info("Max Memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        logger.info("==========================");
    }
    
    /**
     * Initialize server components
     */
    private void initializeComponents() {
        try {
            allowlist.loadAllowlist();
            logger.info("Allowlist initialized with " + allowlist.getAllDevices().size() + " devices");
            
            logger.info("Device monitor initialized");
            
            logger.info("All components initialized successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize components", e);
            throw new RuntimeException("Component initialization failed", e);
        }
    }
    
    /**
     * Start server components
     */
    private void startComponents() {
        try {
            deviceMonitor.start();
            logger.info("Device monitor started");
            
            tcpServer.start();
            logger.info("TCP server started");
            
            // Start TlsServer nếu được khởi tạo
            if (tlsServer != null) {
                logger.info("TLS server started on port " + getTlsPort());
                tlsServer.start();
            }
            
            logger.info("All components started successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start components", e);
            throw new RuntimeException("Component startup failed", e);
        }
    }
    
    /**
     * Add shutdown hook for graceful shutdown
     */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping server gracefully...");
            shutdown();
            logger.info("Server shutdown completed");
        }, "Shutdown-Hook"));
    }
    
    /**
     * Wait for shutdown signal
     */
    public void waitForShutdown() {
        try {
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted");
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }
    
    /**
     * Graceful shutdown of all components
     */
    public void shutdown() {
        if (isShuttingDown.get()) {
            logger.info("Shutdown already in progress");
            return;
        }
        
        isShuttingDown.set(true);
        isRunning.set(false);
        
        logger.info("Initiating graceful shutdown...");
        
        try {
            // Shutdown components in reverse order
            
            if (tcpServer != null) {
                logger.info("Shutting down TCP server...");
                tcpServer.shutdown();
            }
            
            if (tlsServer != null) {
                logger.info("Shutting down TLS server...");
                tlsServer.shutdown();
            }
            
            if (deviceMonitor != null) {
                logger.info("Shutting down device monitor...");
                deviceMonitor.shutdown();
            }
            
            // Wait for active connections to close
            int remainingConnections = activeConnections.get();
            if (remainingConnections > 0) {
                logger.info("Waiting for " + remainingConnections + " active connections to close...");
                int timeout = 30; // 30 seconds timeout
                while (activeConnections.get() > 0 && timeout > 0) {
                    Thread.sleep(1000);
                    timeout--;
                }
                if (activeConnections.get() > 0) {
                    logger.warning("Force closing " + activeConnections.get() + " remaining connections");
                }
            }
            
            logger.info("Server stopped successfully");
            logFinalStats();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during server shutdown", e);
        }
    }
    
    /**
     * Log final server statistics
     */
    private void logFinalStats() {
        long uptime = System.currentTimeMillis() - startTime.get();
        long hours = uptime / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (uptime % (1000 * 60)) / 1000;
        
        logger.info("=== Final Statistics ===");
        logger.info("Total Uptime: " + hours + "h " + minutes + "m " + seconds + "s");
        logger.info("Total Connections: " + totalConnections.get());
        logger.info("Total Commands: " + totalCommands.get());
        logger.info("Peak Active Connections: " + getPeakActiveConnections());
        logger.info("=========================");
    }
    
    // Configuration getters
    private String getServerHost() {
        return config.getProperty("server.host", "0.0.0.0");
    }
    
    private int getServerPort() {
        return Integer.parseInt(config.getProperty("server.port", "9099"));
    }
    
    private int getServerBacklog() {
        return Integer.parseInt(config.getProperty("server.backlog", "50"));
    }
    
    private boolean isTlsEnabled() {
        return Boolean.parseBoolean(config.getProperty("tls.enabled", "false"));
    }
    
    private int getThreadPoolSize() {
        return Integer.parseInt(config.getProperty("server.threadPool.size", "20"));
    }
    
    private int getPollInterval() {
        return Integer.parseInt(config.getProperty("monitor.pollSeconds", "5"));
    }
    
    private String getRouterMode() {
        return config.getProperty("router.mode", "dummy");
    }

    private int getTlsPort() {
        return Integer.parseInt(config.getProperty("tls.port", "443")); // Mặc định là 443
    }
    
    // Component getters
    public Allowlist getAllowlist() {
        return allowlist;
    }
    
    public DeviceMonitor getDeviceMonitor() {
        return deviceMonitor;
    }
    
    public TcpServer getTcpServer() {
        return tcpServer;
    }

    public TlsServer getTlsServer() {
        return tlsServer;
    }
    
    // Server state getters
    public boolean isRunning() {
        return isRunning.get();
    }
    
    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }
    
    public long getUptime() {
        return System.currentTimeMillis() - startTime.get();
    }
    
    public String getFormattedUptime() {
        long uptime = getUptime();
        long hours = uptime / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (uptime % (1000 * 60)) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    // Connection management
    public int getActiveConnectionCount() {
        return activeConnections.get();
    }
    
    public long getTotalConnectionCount() {
        return totalConnections.get();
    }
    
    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
        totalConnections.incrementAndGet();
    }
    
    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }
    
    // Command tracking
    public long getTotalCommandCount() {
        return totalCommands.get();
    }
    
    public void incrementCommandCount() {
        totalCommands.incrementAndGet();
    }
    
    // Performance metrics
    private int peakActiveConnections = 0;
    
    public int getPeakActiveConnections() {
        int current = activeConnections.get();
        if (current > peakActiveConnections) {
            peakActiveConnections = current;
        }
        return peakActiveConnections;
    }
    
    public double getAverageConnectionsPerMinute() {
        long uptimeMinutes = getUptime() / (1000 * 60);
        if (uptimeMinutes == 0) return 0;
        return (double) totalConnections.get() / uptimeMinutes;
    }
    
    public double getAverageCommandsPerMinute() {
        long uptimeMinutes = getUptime() / (1000 * 60);
        if (uptimeMinutes == 0) return 0;
        return (double) totalCommands.get() / uptimeMinutes;
    }
    
    public Properties getConfig() {
        return config;
    }
}
