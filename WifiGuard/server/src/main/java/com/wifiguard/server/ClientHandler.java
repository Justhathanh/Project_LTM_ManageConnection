package com.wifiguard.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.wifiguard.server.model.DeviceInfo;
import com.wifiguard.server.protocol.Command;
import com.wifiguard.server.protocol.Response;

/**
 * Client handler nang cao voi xu ly lenh va validation duoc cai thien
 */
public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    
    // Constants cho configuration
    private static final int DEFAULT_READ_TIMEOUT = 120000; // 120 giây
    private static final int SOCKET_BUFFER_SIZE = 32768; // 32KB
    private static final int MAX_RETRIES = 3;
    private static final int MAX_RETRY_SEND = 2;
    private static final int SUCCESS_SLEEP_MS = 25;
    private static final int RETRY_DELAY_MULTIPLIER = 50;
    private static final int PING_DELAY_MS = 1000;
    private static final String ENCODING = "UTF-8";
    private static final String DEFAULT_HOSTNAME = "Unknown";
    
    // Constants cho performance thresholds
    private static final int SLOW_COMMAND_THRESHOLD_MS = 1000; // 1 giây
    private static final int MILLISECONDS_PER_SECOND = 1000;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int BYTES_PER_KB = 1024;
    private static final int BYTES_PER_MB = 1024 * 1024;
    
    // Constants cho units
    private static final String UNIT_MILLISECONDS = "ms";
    private static final String UNIT_SECONDS = "s";
    private static final String UNIT_MINUTES = "m";
    private static final String UNIT_BYTES = " B";
    private static final String UNIT_KB = " KB";
    private static final String UNIT_MB = " MB";
    private static final String UNIT_BYTES_LABEL = " bytes";
    
    // Constants cho tiếng Việt
    private static final String VIETNAMESE_LAN = " lan";
    private static final String VIETNAMESE_TIMEOUT = " timeout";
    private static final String VIETNAMESE_DONG_KET_NOI = " dong ket noi";
    private static final String VIETNAMESE_DAU = " dau";
    private static final String VIETNAMESE_THU = " thu";
    private static final String VIETNAMESE_CHO_CLIENT = " cho client";
    private static final String VIETNAMESE_DEN = " den";
    private static final String VIETNAMESE_MAT = " mat ";
    private static final String VIETNAMESE_THOI_GIAN = " - Thoi gian: ";
    
    private final Socket clientSocket;
    private final Allowlist allowlist;
    private final DeviceMonitor deviceMonitor;
    private final ServerMain serverMain;
    
    private BufferedReader reader;
    private PrintWriter writer;
    private String clientAddress;
    private int readTimeout; // Them bien readTimeout vao class scope
    
    // Cac chi so hieu suat
    private final long connectionStartTime;
    private final AtomicLong commandsProcessed;
    private final AtomicLong bytesReceived;
    private final AtomicLong bytesSent;
    
    public ClientHandler(Socket clientSocket, Allowlist allowlist, DeviceMonitor deviceMonitor, ServerMain serverMain) {
        this.clientSocket = clientSocket;
        this.allowlist = allowlist;
        this.deviceMonitor = deviceMonitor;
        this.serverMain = serverMain;
        this.clientAddress = clientSocket.getInetAddress().getHostAddress();
        
        // Khoi tao cac chi so
        this.connectionStartTime = System.currentTimeMillis();
        this.commandsProcessed = new AtomicLong(0);
        this.bytesReceived = new AtomicLong(0);
        this.bytesSent = new AtomicLong(0);
    }
    
    @Override
    public void run() {
        try {
            serverMain.incrementActiveConnections();
            setupStreams();
            handleClient();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Loi xu ly client: " + clientAddress, e);
        } finally {
            serverMain.decrementActiveConnections();
            logConnectionStats();
            cleanup();
        }
    }
    
    /**
     * Thiet lap input/output streams voi cau hinh socket phu hop
     */
    private void setupStreams() throws IOException {
        // Lay gia tri timeout tu cau hinh - TANG LEN 120 GIAY de giam PING
        String readTimeoutStr = serverMain.getConfig().getProperty("server.readTimeout", String.valueOf(DEFAULT_READ_TIMEOUT));
        this.readTimeout = Integer.parseInt(readTimeoutStr);
        
        // Cau hinh socket
        clientSocket.setSoTimeout(this.readTimeout);
        clientSocket.setKeepAlive(true);
        clientSocket.setTcpNoDelay(true);
        clientSocket.setReuseAddress(true);
        
        // Tang buffer size dang ke
        clientSocket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
        clientSocket.setSendBufferSize(SOCKET_BUFFER_SIZE);
        
        // Su dung UTF-8 encoding
        reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), ENCODING));
        writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), ENCODING), true);
        
        logger.info("Streams da duoc cau hinh" + VIETNAMESE_CHO_CLIENT + ": " + clientAddress + 
                   " (timeout: " + this.readTimeout + UNIT_MILLISECONDS + ", encoding: " + ENCODING + ", buffer: " + (SOCKET_BUFFER_SIZE/BYTES_PER_KB) + UNIT_KB + ")");
    }
    
    /**
     * Vong lap xu ly client chinh
     */
    private void handleClient() throws IOException {
        logger.info("Dang xu ly client: " + clientAddress);
        
        sendResponse(Response.success("Chao mung den voi WifiGuard Server"));
        
        String inputLine;
        int retryCount = 0;
        
        while (true) {
            try {
                // Doc lenh tu client voi timeout
                inputLine = reader.readLine();
                
                if (inputLine == null) {
                    logger.info("Client da ngat ket noi: " + clientAddress);
                    break;
                }
                
                inputLine = inputLine.trim();
                logger.info("Nhan duoc tu client: '" + inputLine + "' (length: " + inputLine.length() + ")");
                
                if (inputLine.isEmpty()) {
                    logger.info("Bo qua lenh rong");
                    continue;
                }
                
                // Reset retry count khi nhan duoc lenh thanh cong
                retryCount = 0;
                
                // Theo doi bytes nhan duoc
                bytesReceived.addAndGet(inputLine.length());
                
                logger.info("Dang xu ly lenh: " + inputLine);
                
                long startTime = System.currentTimeMillis();
                Response response = executeCommand(inputLine);
                long processingTime = System.currentTimeMillis() - startTime;
                
                // Theo doi xu ly lenh
                commandsProcessed.incrementAndGet();
                serverMain.incrementCommandCount();
                
                // Ghi log chi so hieu suat cho cac lenh cham
                if (processingTime > SLOW_COMMAND_THRESHOLD_MS) {
                    logger.warning("Xu ly lenh cham: " + inputLine + VIETNAMESE_MAT + processingTime + UNIT_MILLISECONDS);
                }
                
                logger.info("Gui response: " + response.toString());
                sendResponse(response);
                
                // Khong gui confirmation nua de tranh lap
                if (shouldQuit(inputLine, response)) {
                    logger.info("Client yeu cau thoat");
                    break;
                }
                
            } catch (java.net.SocketTimeoutException e) {
                retryCount++;
                logger.warning("Socket timeout" + VIETNAMESE_LAN + " " + retryCount + VIETNAMESE_CHO_CLIENT + ": " + clientAddress + " (" + this.readTimeout + UNIT_MILLISECONDS + ")");
                
                if (retryCount >= MAX_RETRIES) {
                    logger.warning("Da timeout " + MAX_RETRIES + " lan, dong ket noi: " + clientAddress);
                    break;
                }
                
                // Chi gui PING khi thuc su can thiet - giam so lan PING
                if (retryCount == 1) {
                    // Lan dau timeout - khong gui PING, chi log
                    logger.info("Lan dau timeout, khong gui PING - cho client doc response");
                } else {
                    // Lan 2, 3 - gui PING de kiem tra ket noi
                    try {
                        logger.info("Gui PING" + VIETNAMESE_THU + " " + retryCount + " de kiem tra ket noi");
                        sendResponse(Response.success("PING - Kiem tra ket noi"));
                        
                        // Doi mot chut truoc khi thu lai
                        Thread.sleep(PING_DELAY_MS);
                    } catch (Exception pingEx) {
                        logger.warning("Khong the gui PING, dong ket noi: " + clientAddress);
                        break;
                    }
                }
                
            } catch (IOException e) {
                logger.log(Level.WARNING, "Loi doc tu client: " + clientAddress, e);
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Loi khong mong doi tu client: " + clientAddress, e);
                break;
            }
        }
        
        logger.info("Ket thuc xu ly client: " + clientAddress);
    }
    
    /**
     * Ghi log thong ke ket noi
     */
    private void logConnectionStats() {
        long connectionDuration = System.currentTimeMillis() - connectionStartTime;
        long commands = commandsProcessed.get();
        long received = bytesReceived.get();
        long sent = bytesSent.get();
        
        logger.info("Thong ke ket noi cho " + clientAddress + 
                   VIETNAMESE_THOI_GIAN + formatDuration(connectionDuration) +
                   ", Lenh: " + commands +
                   ", Nhan: " + formatBytes(received) +
                   ", Gui: " + formatBytes(sent));
    }
    
    /**
     * Dinh dang thoi gian theo cach doc duoc
     */
    private String formatDuration(long milliseconds) {
        if (milliseconds < MILLISECONDS_PER_SECOND) return milliseconds + UNIT_MILLISECONDS;
        if (milliseconds < MILLISECONDS_PER_SECOND * SECONDS_PER_MINUTE) return (milliseconds / MILLISECONDS_PER_SECOND) + UNIT_SECONDS;
        return (milliseconds / (MILLISECONDS_PER_SECOND * SECONDS_PER_MINUTE)) + UNIT_MINUTES + ((milliseconds % (MILLISECONDS_PER_SECOND * SECONDS_PER_MINUTE)) / MILLISECONDS_PER_SECOND) + UNIT_SECONDS;
    }
    
    /**
     * Dinh dang bytes theo cach doc duoc
     */
    private String formatBytes(long bytes) {
        if (bytes < BYTES_PER_KB) return bytes + UNIT_BYTES;
        if (bytes < BYTES_PER_MB) return String.format("%.1f" + UNIT_KB, bytes / (double)BYTES_PER_KB);
        return String.format("%.1f" + UNIT_MB, bytes / (double)BYTES_PER_MB);
    }
    
    /**
     * Thuc thi mot lenh va tra ve response
     */
    private Response executeCommand(String inputLine) {
        try {
            String[] parts = inputLine.split("\\s+", 4); // Ho tro toi da 4 phan cho lenh ADD
            String commandStr = parts[0].toUpperCase();
            
            if (commandStr.isEmpty()) {
                return Response.error("Lenh rong");
            }
            
            // Su dung Command enum nang cao de phan tich va validation
            Command command;
            try {
                command = Command.parse(commandStr);
            } catch (IllegalArgumentException e) {
                return Response.error(e.getMessage() + ". Go 'HELP' de xem cac lenh co san.");
            }
            
            // Kiem tra so luong tham so
            int argCount = parts.length - 1;
            String validationMessage = command.getArgValidationMessage(argCount);
            if (validationMessage != null) {
                return Response.error(validationMessage);
            }
            
            switch (command) {
                case LIST:
                    return handleListCommand();
                case ALLOWLIST:
                    return handleAllowlistCommand();
                case ADD:
                    return handleAddCommand(parts);
                case DEL:
                    return handleDelCommand(parts);
                case STATUS:
                    return handleStatusCommand();
                case QUIT:
                    return Response.success("Tam biet!");
                default:
                    return Response.error("Lenh chua duoc implement: " + command);
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Loi thuc thi lenh: " + inputLine, e);
            return Response.error("Loi server noi bo: " + e.getMessage());
        }
    }
    
    /**
     * Xu ly lenh LIST
     */
    private Response handleListCommand() {
        try {
            logger.info("Dang xu ly lenh LIST");
            
            if (deviceMonitor == null) {
                logger.severe("DeviceMonitor la null!");
                return Response.error("Loi: DeviceMonitor khong duoc khoi tao");
            }
            
            // Lay thiết bị từ DeviceMonitor (thiết bị được phát hiện trên mạng)
            List<DeviceInfo> discoveredDevices = deviceMonitor.getAllDevices();
            logger.info("Lay duoc " + (discoveredDevices != null ? discoveredDevices.size() : "null") + " thiet bi tu DeviceMonitor");
            
            if (discoveredDevices == null) {
                logger.warning("DeviceMonitor.getAllDevices() tra ve null");
                return Response.error("Loi: Khong the lay danh sach thiet bi");
            }
            
            if (discoveredDevices.isEmpty()) {
                return Response.success("Khong co thiet bi nao duoc phat hien tren mang");
            }
            
            logger.info("Tim thay " + discoveredDevices.size() + " thiet bi tren mang");
            return Response.success("Tim thay " + discoveredDevices.size() + " thiet bi tren mang", discoveredDevices);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Loi trong handleListCommand", e);
            return Response.error("Loi khi xu ly lenh LIST: " + e.getMessage());
        }
    }
    
    /**
     * Xu ly lenh ALLOWLIST
     */
    private Response handleAllowlistCommand() {
        try {
            logger.info("Dang xu ly lenh ALLOWLIST");
            
            // Lay thiết bị từ allowlist (thiết bị được phép)
            List<DeviceInfo> allowedDevices = allowlist.getAllDevices();
            logger.info("Lay duoc " + (allowedDevices != null ? allowedDevices.size() : "null") + " thiet bi tu allowlist");
            
            if (allowedDevices == null) {
                logger.warning("Allowlist.getAllDevices() tra ve null");
                return Response.error("Loi: Khong the lay danh sach allowlist");
            }
            
            if (allowedDevices.isEmpty()) {
                return Response.success("Khong co thiet bi nao trong allowlist");
            }
            
            logger.info("Tim thay " + allowedDevices.size() + " thiet bi trong allowlist");
            return Response.success("Tim thay " + allowedDevices.size() + " thiet bi trong allowlist", allowedDevices);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Loi trong handleAllowlistCommand", e);
            return Response.error("Loi khi xu ly lenh ALLOWLIST: " + e.getMessage());
        }
    }
    
    /**
     * Xu ly lenh ADD su dung DeviceInfo builder
     */
    private Response handleAddCommand(String[] parts) {
        String mac = parts[1];
        String hostname = parts.length > 2 ? parts[2] : DEFAULT_HOSTNAME;
        String ip = parts.length > 3 ? parts[3] : "";
        
        logger.info("Dang xu ly lenh ADD: MAC=" + mac + ", HOSTNAME=" + hostname + ", IP=" + ip);
        
        // Su dung DeviceInfo builder de validation
        DeviceInfo device = DeviceInfo.builder()
                .mac(mac)
                .hostname(hostname)
                .ip(ip)
                .buildOrNull();
        
        if (device == null) {
            logger.warning("DeviceInfo builder tra ve null cho MAC: " + mac);
            return Response.error("Dinh dang MAC khong hop le: " + mac + 
                               ". Dinh dang mong doi: XX:XX:XX:XX:XX:XX");
        }
        
        logger.info("DeviceInfo da tao: " + device.toCompactString() + ", isValid=" + device.isValid());
        
        if (!device.isValid()) {
            String errors = device.getValidationErrors();
            logger.warning("Validation thiet bi that bai: " + errors);
            return Response.error("Validation thiet bi that bai: " + errors);
        }
        
        try {
            logger.info("Dang them thiet bi vao allowlist: " + mac);
            boolean added = allowlist.addDevice(device);
            
            if (added) {
                logger.info("Thiet bi da duoc them vao allowlist: " + mac + " (" + hostname + ") boi " + clientAddress);
                return Response.success("Thiet bi da duoc them thanh cong: " + mac);
            } else {
                logger.info("Thiet bi voi MAC " + mac + " da co trong allowlist");
                return Response.error("Thiet bi voi MAC " + mac + " da co trong allowlist");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Loi them thiet bi: " + mac, e);
            return Response.error("Khong the them thiet bi: " + e.getMessage());
        }
    }
    
    /**
     * Xu ly lenh DEL
     */
    private Response handleDelCommand(String[] parts) {
        String mac = parts[1];
        
        // Kiem tra MAC address su dung DeviceInfo
        if (!DeviceInfo.createFromMac(mac).hasValidMac()) {
            return Response.error("Dinh dang MAC khong hop le: " + mac + 
                               ". Dinh dang mong doi: XX:XX:XX:XX:XX:XX");
        }
        
        try {
            boolean removed = allowlist.removeDevice(mac);
            
            if (removed) {
                logger.info("Thiet bi da duoc xoa khoi allowlist: " + mac + " boi " + clientAddress);
                return Response.success("Thiet bi da duoc xoa thanh cong: " + mac);
            } else {
                return Response.error("Thiet bi voi MAC " + mac + " khong tim thay trong allowlist");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Loi xoa thiet bi: " + mac, e);
            return Response.error("Khong the xoa thiet bi: " + e.getMessage());
        }
    }
    
    /**
     * Xu ly lenh STATUS
     */
    private Response handleStatusCommand() {
        try {
            logger.info("Dang xu ly lenh STATUS");
            
            // Test response don gian truoc
            String simpleStatus = "Server OK - " + System.currentTimeMillis();
            logger.info("Tao status don gian: " + simpleStatus);
            
            return Response.success(simpleStatus);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Loi trong handleStatusCommand", e);
            return Response.error("Loi khi xu ly lenh STATUS: " + e.getMessage());
        }
    }
    
    /**
     * Kiem tra xem lenh co nen thoat khong
     */
    private boolean shouldQuit(String inputLine, Response response) {
        return inputLine.equalsIgnoreCase("QUIT") || 
               response.getStatus() == Response.Status.ERROR && 
               response.getMessage().contains("Tam biet");
    }
    
    /**
     * Gui response den client
     */
    private void sendResponse(Response response) {
        try {
            // Su dung format response dep thay vi format cu
            String responseStr = response.toBeautifulString();
            logger.info("Dang gui response dep: " + responseStr);
            
            // Gui response voi retry logic don gian hon
            int retryCount = 0;
            boolean sent = false;
            
            while (!sent && retryCount < MAX_RETRY_SEND) {
                try {
                    writer.println(responseStr);
                    writer.flush();
                    
                    // Doi mot chut de dam bao response duoc gui
                    Thread.sleep(SUCCESS_SLEEP_MS);
                    
                    // Kiem tra xem writer co loi khong
                    if (writer.checkError()) {
                        throw new IOException("Writer error detected");
                    }
                    
                    sent = true;
                    logger.info("Da gui response thanh cong" + VIETNAMESE_DEN + " " + clientAddress + " (" + responseStr.length() + UNIT_BYTES_LABEL + ")");
                    
                } catch (Exception e) {
                    retryCount++;
                    logger.warning("Loi gui response" + VIETNAMESE_LAN + " " + retryCount + VIETNAMESE_DEN + " " + clientAddress + ": " + e.getMessage());
                    
                    if (retryCount >= MAX_RETRY_SEND) {
                        // Khong throw exception nua, chi log warning
                        logger.warning("Khong the gui response sau " + MAX_RETRY_SEND + " lan thu, nhung khong dong ket noi");
                        break; // Thoat vong lap thay vi throw exception
                    }
                    
                    // Doi mot chut truoc khi thu lai
                    Thread.sleep(RETRY_DELAY_MULTIPLIER * retryCount);
                }
            }
            
            // Theo doi bytes da gui neu thanh cong
            if (sent) {
                bytesSent.addAndGet(responseStr.length());
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Loi gui response" + VIETNAMESE_DEN + " " + clientAddress, e);
        }
    }
    
    /**
     * Don dep tai nguyen
     */
    private void cleanup() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Loi trong qua trinh don dep cho " + clientAddress, e);
        }
    }
    
    // Cac getter cho chi so
    public long getConnectionDuration() { return System.currentTimeMillis() - connectionStartTime; }
    public long getCommandsProcessed() { return commandsProcessed.get(); }
    public long getBytesReceived() { return bytesReceived.get(); }
    public long getBytesSent() { return bytesSent.get(); }
    public String getClientAddress() { return clientAddress; }
}
