package com.wifiguard.server;

import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class SecurityConfig {
    public final boolean tlsEnabled;
    public final String keystorePath;
    public final String keystorePassword;

    public SecurityConfig(ServerConfig cfg) {
        this.tlsEnabled = Boolean.parseBoolean(cfg.props.getProperty("server.tls.enabled", "false"));
        this.keystorePath = cfg.props.getProperty("server.tls.keystore", "");
        this.keystorePassword = cfg.props.getProperty("server.tls.password", "");
    }

    public SSLContext buildSSLContext() throws Exception {
        if (!tlsEnabled) return null;
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, keystorePassword.toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }
}
