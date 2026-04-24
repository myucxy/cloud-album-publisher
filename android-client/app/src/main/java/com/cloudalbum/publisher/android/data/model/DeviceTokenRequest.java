package com.cloudalbum.publisher.android.data.model;

public class DeviceTokenRequest {
    private final String deviceUid;

    public DeviceTokenRequest(String deviceUid) {
        this.deviceUid = deviceUid;
    }

    public String getDeviceUid() {
        return deviceUid;
    }
}
