package com.wifiguard.client.ui;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.security.SecureRandom;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ClientApi implements Closeable {
    private final boolean useTls;
    private java.net.Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientApi(boolean useTls) {
        this.useTls = useTls;
    }

    public void connect(String host, int port) throws Exception {
        if (useTls) {
            SSLContext ctx = SSLContext.getInstance("TLS");
            // DEV ONLY: trust-all. Khi production, thay bằng truststore.
            ctx.init(null, new TrustManager[]{ trustAll() }, new SecureRandom());
            SSLSocketFactory f = ctx.getSocketFactory();
            socket = f.createSocket(host, port);
        } else {
            socket = SocketFactory.getDefault().createSocket(host, port);
        }
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    public String send(String line) throws IOException {
        if (socket == null || socket.isClosed()) throw new IllegalStateException("Not connected");
        out.println(line);
        out.flush();
        
        // Read full response (multiple lines)
        StringBuilder response = new StringBuilder();
        String line2;
        int lineCount = 0;
        int maxLines = 50; // Prevent infinite loop
        
        while ((line2 = in.readLine()) != null && lineCount < maxLines) {
            response.append(line2).append("\n");
            lineCount++;
            
            // Stop if we see end markers
            if (line2.contains("+--------------------------------------------------") || 
                line2.contains("End of response") ||
                line2.isEmpty()) {
                break;
            }
        }
        
        return response.toString().trim();
    }

    @Override
    public void close() throws IOException {
        try { if (out != null) out.close(); } catch (Exception ignore) {}
        try { if (in  != null) in.close();  } catch (Exception ignore) {}
        try { if (socket != null) socket.close(); } catch (Exception ignore) {}
    }

    private static X509TrustManager trustAll() {
        return new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String s) {}
            public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String s) {}
        };
    }
}