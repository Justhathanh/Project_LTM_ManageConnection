# WifiGuard Server

A robust network device monitoring and access control server with TCP/TLS support.

## Features

### Core Functionality
- **Multi-client TCP Server**: Handles multiple client connections simultaneously
- **TLS Support**: Optional SSL/TLS encryption for secure communications
- **Device Discovery**: Automatically scans network for connected devices
- **Access Control**: Maintains allowlist of authorized devices
- **Real-time Monitoring**: Continuously polls network for device status changes

### Commands Supported
- `LIST` - Display all discovered devices
- `ADD <MAC> [HOSTNAME] [IP]` - Add device to allowlist
- `DEL <MAC>` - Remove device from allowlist
- `STATUS` - Get server status and statistics
- `QUIT` - Close client connection

### Device Information
- **IP Address**: Current IP address of the device
- **MAC Address**: Unique hardware identifier
- **Hostname**: Device name/identifier
- **Last Seen**: Timestamp of last activity
- **Known Status**: Whether device is in allowlist

## Architecture

```
ServerMain (Entry Point)
├── TcpServer (Network Layer)
│   ├── Regular TCP Socket
│   └── TLS Socket (optional)
├── ClientHandler (Command Processing)
├── DeviceMonitor (Device Discovery)
└── Allowlist (Access Control)
```

## Configuration

### Server Properties (`server.properties`)

```properties
# Server settings
server.port=9099
server.host=0.0.0.0
server.backlog=50

# TLS settings
tls.enabled=false
tls.keystore=server.jks
tls.keystorePassword=wifiguard123

# Device monitoring
monitor.pollSeconds=5
monitor.banSeconds=600
monitor.networkScanRange=254
monitor.pingTimeout=1000

# Router integration
router.mode=dummy
router.openwrt.host=192.168.1.1
router.openwrt.username=admin
```

### Logging Configuration (`logging.properties`)

```properties
# Log levels and handlers
handlers=java.util.logging.FileHandler, java.util.logging.ConsoleHandler
.level=INFO

# File logging
java.util.logging.FileHandler.pattern=server.log
java.util.logging.FileHandler.limit=10MB
java.util.logging.FileHandler.count=5
```

## Building and Running

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build
```bash
mvn clean compile
```

### Run
```bash
java -cp "target/classes" com.wifiguard.server.ServerMain
```

### Package with Dependencies
```bash
mvn clean package
java -jar target/wifiguard-server-1.0.0-jar-with-dependencies.jar
```

## Testing

### Quick Test (Không sử dụng telnet)

#### Method 1: Java Test Client (Recommended)
```bash
# Start server
java -cp "target/classes" com.wifiguard.server.ServerMain

# In another terminal, run test client
java -cp "target/classes" TestClient
```

#### Method 2: Manual Testing
```bash
# Terminal 1: Start server
java -cp "target/classes" com.wifiguard.server.ServerMain

# Terminal 2: Test client
java -cp "target/classes;target/test-classes" TestClient
```

#### Method 3: Manual Testing
```bash
# Terminal 1: Start server
java -cp "target/classes" com.wifiguard.server.ServerMain

# Terminal 2: Test with any TCP client
# You can use netcat, PuTTY, or write a simple client
```

### Test Commands
Once connected, you can test these commands:
```
LIST                    # List all discovered devices
STATUS                  # Get server status
ADD 00:1B:44:01:02:03 TestDevice 192.168.1.100
LIST                    # Verify device was added
DEL 00:1B:44:01:02:03  # Remove device
LIST                    # Verify device was removed
QUIT                    # Exit connection
```

### Alternative TCP Clients for Windows

If you prefer not to use the Java test client:

1. **PuTTY** (Free SSH/Telnet client)
   - Download from: https://www.putty.org/
   - Use Connection type: Raw
   - Host: localhost, Port: 9099

2. **Netcat for Windows**
   - Download from: https://eternallybored.org/misc/netcat/
   - Command: `nc localhost 9099`

3. **PowerShell Test-NetConnection**
   ```powershell
   Test-NetConnection -ComputerName localhost -Port 9099
   ```

4. **Online TCP Client**
   - Use online tools like: https://www.tcpclient.com/
   - Host: localhost, Port: 9099

## Network Discovery Modes

### Dummy Mode (Default)
- Scans local network using ICMP ping
- Generates deterministic MAC addresses from IP
- Suitable for development and testing

### OpenWrt Mode (Planned)
- Integrates with OpenWrt router APIs
- Real device information from router
- Production-ready for OpenWrt environments

## Security Features

### TLS Support
- TLS 1.2 and 1.3 protocols
- Configurable keystore and passwords
- Secure client-server communication

### Input Validation
- MAC address format validation
- IP address format validation
- Command argument validation
- SQL injection prevention

### Access Control
- Device allowlist management
- Persistent storage in allowlist.txt
- Audit logging for all operations

## Performance Features

### Thread Management
- Configurable thread pool size
- Daemon threads for background tasks
- Efficient connection handling

### Resource Management
- Automatic cleanup of old devices
- Configurable timeouts
- Memory-efficient data structures

## File Structure

```
server/
├── src/main/java/com/wifiguard/server/
│   ├── ServerMain.java          # Main entry point
│   ├── TcpServer.java           # TCP/TLS server
│   ├── ClientHandler.java       # Client command handler
│   ├── DeviceMonitor.java       # Device discovery
│   ├── Allowlist.java           # Access control
│   ├── model/
│   │   └── DeviceInfo.java      # Device data model
│   └── protocol/
│       ├── Command.java         # Command enum
│       └── Response.java        # Response model
├── src/main/resources/
│   ├── server.properties        # Server configuration
│   └── logging.properties       # Logging configuration
├── allowlist.txt                # Device allowlist
├── pom.xml                      # Maven configuration
└── README.md                    # This file
```

## Troubleshooting

### Common Issues

1. **Port Already in Use**
   - Change `server.port` in configuration
   - Check for other services using the port

2. **Permission Denied**
   - Run with appropriate permissions
   - Check file write permissions for allowlist.txt

3. **TLS Connection Failed**
   - Verify keystore file exists
   - Check keystore passwords
   - Ensure TLS is enabled in configuration

4. **Device Discovery Issues**
   - Check network connectivity
   - Verify firewall settings
   - Adjust ping timeout values

### Logging
- Check `server.log` for detailed information
- Adjust log levels in `logging.properties`
- Monitor console output for real-time status

## Development

### Adding New Commands
1. Add command to `Command` enum
2. Implement handler in `Client