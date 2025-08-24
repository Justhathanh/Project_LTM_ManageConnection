package com.wifiguard.server;
import java.io.*; import java.net.Socket;
public class ClientHandler implements Runnable {
  private final Socket s; private final Allowlist allow; private final DeviceMonitor mon; private PrintWriter out;
  public ClientHandler(Socket s, Allowlist a, DeviceMonitor m){ this.s=s; this.allow=a; this.mon=m; }
  @Override public void run(){
    try(BufferedReader br=new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter pw=new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())),true)){
      out=pw; send("WELCOME WiFiGuard. Commands: LIST | ADD <mac> | DEL <mac> | QUIT");
      String line; while((line=br.readLine())!=null){
        String[] p=line.trim().split("\\s+"); if(p.length==0) continue;
        switch(p[0].toUpperCase()){
          case "LIST" -> { for(String m: allow.all()) send("ALLOW "+m); send("OK"); }
          case "ADD"  -> { if(p.length<2){send("ERR");break;} allow.add(norm(p[1])); send("OK"); }
          case "DEL"  -> { if(p.length<2){send("ERR");break;} allow.remove(norm(p[1])); send("OK"); }
          case "QUIT" -> { return; }
          default -> send("ERR");
        }
      }
    } catch(Exception ignored){}
  }
  private static String norm(String mac){ String m=mac.toLowerCase().replace('-',':'); if(!m.contains(":")&&m.length()==12){ StringBuilder sb=new StringBuilder(); for(int i=0;i<12;i+=2){ if(i>0)sb.append(':'); sb.append(m, i,i+2);} m=sb.toString(); } return m; }
  public void send(String s){ if(out!=null) out.println(s); }
}
