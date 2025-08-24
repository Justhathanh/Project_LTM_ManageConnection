package com.wifiguard.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfig {
    public final Properties props = new Properties();

    public final int port;
    public final int pollSeconds;
    public final int banSeconds;
    public final String routerMode;
    public final String owrtBase;
    public final String owrtUser;
    public final String owrtPass;
    public final String owrtHostapd;

    public ServerConfig() {
        try (InputStream in = ServerConfig.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {}
        port = Integer.parseInt(props.getProperty("server.port", "9099"));
        pollSeconds = Integer.parseInt(props.getProperty("monitor.pollSeconds", "5"));
        banSeconds = Integer.parseInt(props.getProperty("monitor.banSeconds", "0"));
        routerMode = props.getProperty("router.mode", "windowsarp");
        owrtBase = props.getProperty("openwrt.base", "http://192.168.1.1");
        owrtUser = props.getProperty("openwrt.user", "root");
        owrtPass = props.getProperty("openwrt.pass", "password");
        owrtHostapd = props.getProperty("openwrt.hostapdObject", "hostapd.wlan0");
    }
}
