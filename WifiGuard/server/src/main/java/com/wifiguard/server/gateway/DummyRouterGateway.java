package com.wifiguard.server.gateway;

import com.wifiguard.server.model.DeviceInfo;
import java.time.LocalDateTime;
import java.util.*;

public class DummyRouterGateway implements RouterGateway {
    private int counter = 0;

    @Override
    public List<DeviceInfo> ScanDevices() {
        List<DeviceInfo> devices = new ArrayList<>();
        devices.add(DeviceInfo.builder()
                .mac("AA:BB:CC:DD:EE:01")
                .ip("192.168.1.2")
                .hostname("Laptop")
                .lastSeen(LocalDateTime.now())
                .known(true)
                .build());

        if (counter % 2 == 0) {
            devices.add(DeviceInfo.builder()
                    .mac("AA:BB:CC:DD:EE:02")
                    .ip("192.168.1.3")
                    .hostname("Phone")
                    .lastSeen(LocalDateTime.now())
                    .build());
        }

        counter++;
        return devices;
    }

    @Override
    public void refresh(){};
    public boolean isAvailable(){
        System.out.println("Called");
        return true;};
}
