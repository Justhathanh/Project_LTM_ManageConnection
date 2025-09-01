package com.wifiguard.server;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class SecurityConfig {
    private static final Logger logger = Logger.getLogger(SecurityConfig.class.getName());
    
    public final boolean tlsEnabled;
    public final String keystorePath;
    public final String keystorePassword;

    public SecurityConfig(ServerConfig cfg) {
        this.tlsEnabled = Boolean.parseBoolean(cfg.props.getProperty("server.tls.enabled", "false"));
        this.keystorePath = cfg.props.getProperty("server.tls.keystore", "server.jks");
        this.keystorePassword = cfg.props.getProperty("server.tls.password", "wifiguard123");
        
        // Tạo keystore tự động nếu TLS được bật và keystore không tồn tại
        if (this.tlsEnabled) {
            createKeystoreIfNeeded();
        }
    }

    private void createKeystoreIfNeeded() {
        try {
            java.io.File keystoreFile = new java.io.File(keystorePath);
            if (!keystoreFile.exists()) {
                logger.info("Creating new keystore: " + keystorePath);
                createSimpleKeystore();
                logger.info("Keystore created successfully");
            } else {
                logger.info("Using existing keystore: " + keystorePath);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating keystore", e);
        }
    }
    
    private void createSimpleKeystore() throws Exception {
        try {
            // Sử dụng keytool command để tạo keystore
            String keytoolCommand = String.format(
                "keytool -genkeypair -alias wifiguard -keyalg RSA -keysize 2048 " +
                "-keystore %s -storepass %s -keypass %s " +
                "-dname \"CN=WifiGuard Server, OU=IT, O=WifiGuard, L=City, S=State, C=VN\" " +
                "-validity 365 -storetype JKS",
                keystorePath, keystorePassword, keystorePassword
            );
            
            logger.info("Creating keystore using keytool: " + keytoolCommand);
            
            Process process = Runtime.getRuntime().exec(keytoolCommand);
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("Keystore created successfully using keytool");
            } else {
                // Fallback: tạo keystore cơ bản
                logger.warning("keytool failed, trying fallback method");
                createBasicKeystore();
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "keytool method failed, using fallback", e);
            createBasicKeystore();
        }
    }
    
    private void createBasicKeystore() throws Exception {
        // Tạo keystore cơ bản với key đơn giản
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null, keystorePassword.toCharArray());
        
        // Tạo key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        
        // Lưu vào keystore với key pair (không cần certificate phức tạp)
        // Sử dụng setKeyEntry với certificate chain rỗng
        keystore.setKeyEntry("wifiguard", keyPair.getPrivate(), keystorePassword.toCharArray(), 
                           new Certificate[0]);
        
        // Lưu keystore ra file
        try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
            keystore.store(fos, keystorePassword.toCharArray());
        }
        
        logger.info("Basic keystore created with RSA key pair");
    }
    
    public SSLContext buildSSLContext() throws Exception {
        if (!tlsEnabled) return null;
        
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, keystorePassword.toCharArray());
            }
            
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, keystorePassword.toCharArray());
            
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            
            logger.info("SSL Context created successfully");
            return ctx;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error building SSL Context", e);
            throw e;
        }
    }
}
