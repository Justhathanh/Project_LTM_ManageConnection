package com.wifiguard.server.gateway;

import com.wifiguard.server.model.*;

import java.util.List;

public interface RouterGateway{
 
    List<DeviceInfo> ScanDevices();

    void refresh();

    boolean isAvailable();

}