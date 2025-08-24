package com.wifiguard.server;
import com.wifiguard.server.gateway.RouterGateway; import java.util.Map;
public class DeviceMonitor implements Runnable {
  private final RouterGateway gw; private final Allowlist allow; private final int pollSec;
  public DeviceMonitor(RouterGateway gw, Allowlist allow, int pollSec, int banSec){ this.gw=gw; this.allow=allow; this.pollSec=pollSec; }
  @Override public void run(){
    while(true){ try{
      Map<String,RouterGateway.Device> m = gw.getConnectedDevices();
      for (var d: m.values()){ if(!allow.contains(d.mac())) System.out.println("EVENT UNKNOWN "+d.mac()); System.out.println("EVENT CONNECTED "+d.mac()); }
      Thread.sleep(Math.max(1,pollSec)*1000L);
    }catch(Exception e){ System.out.println("EVENT ERROR "+e.getMessage()); try{Thread.sleep(1000);}catch(Exception ignored){} } }
  }
}
