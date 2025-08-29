package com.wifiguard.ui;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.*;
import java.security.SecureRandom;

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
        return in.readLine(); // server trả 1 dòng / lệnh
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