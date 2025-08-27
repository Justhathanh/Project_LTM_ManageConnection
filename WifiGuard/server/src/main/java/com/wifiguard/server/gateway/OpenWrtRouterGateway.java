package com.wifiguard.server.gateway;


import com.wifiguard.server.model.DeviceInfo;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Giám sát thiết bị trong mạng và phát hiện thiết bị mới.
 */
public class OpenWrtRouterGateway {

    private final RouterGateway gateway;
    private final List<DeviceInfo> knownDevices = new CopyOnWriteArrayList<>();

    public OpenWrtRouterGateway(RouterGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Quét và phát hiện thiết bị mới.
     * @return danh sách các thiết bị mới xuất hiện kể từ lần quét trước
     */
    public List<DeviceInfo> scanAndDetectNewDevices() {
        List<DeviceInfo> currentDevices = gateway.ScanDevices();
        List<DeviceInfo> newDevices = new ArrayList<>();

        for (DeviceInfo device : currentDevices) {
            if (!knownDevices.contains(device)) {
                newDevices.add(device);
                System.out.println("[NEW DEVICE] " + device.toCompactString());
            }
        }

        // cập nhật danh sách knownDevices
        knownDevices.clear();
        knownDevices.addAll(currentDevices);

        return newDevices;
    }

    /**
     * Lấy toàn bộ danh sách hiện tại
     */
    public List<DeviceInfo> getCurrentDevices() {
        return new ArrayList<>(knownDevices);
    }
}
