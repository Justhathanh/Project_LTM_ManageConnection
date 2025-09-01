package com.wifiguard.server.gateway;
import java.util.Map;
public interface RouterGateway {
  Map<String,Device> getConnectedDevices() throws Exception;
  default void block(String mac,int banSeconds) throws Exception {}
  record Device(String mac,int rssi,int txRateMbps){}
}

