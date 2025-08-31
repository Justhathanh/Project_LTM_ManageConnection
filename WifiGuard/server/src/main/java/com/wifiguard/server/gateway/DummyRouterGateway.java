package com.wifiguard.server.gateway;
import java.util.Map;
public class DummyRouterGateway implements RouterGateway {
  @Override public Map<String,Device> getConnectedDevices() {
    return Map.of("aa:bb:cc:11:22:33", new Device("aa:bb:cc:11:22:33",-50,150));
  }
}
