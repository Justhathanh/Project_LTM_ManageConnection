package com.wifiguard.client;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Clientmain {
    public static void main(String[] args) throws Exception {
        String host = args.length>0 ? args[0] : "127.0.0.1";
        int port = args.length>1 ? Integer.parseInt(args[1]) : 9099;
        boolean tls = args.length>2 && args[2].equalsIgnoreCase("tls");

        Socket s;
        if (tls) {
            SSLSocketFactory fac = (SSLSocketFactory) SSLSocketFactory.getDefault();
            s = fac.createSocket(host, port);
            ((SSLSocket) s).startHandshake();
        } else {
            s = new Socket(host, port);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true);

        Thread rx = new Thread(() -> {
            try { String line; while ((line = br.readLine()) != null) System.out.println(line); }
            catch (IOException ignored) {}
        });
        rx.setDaemon(true);
        rx.start();

        Scanner sc = new Scanner(System.in);
        while (true) {
            String line = sc.nextLine();
            if (line == null) break;
            pw.println(line);
            if ("QUIT".equalsIgnoreCase(line)) break;
        }
        s.close();
    }
}