package com.cloudalbum.publisher.android.data.model;

public class DeviceSelfRegisterRequest {
    private final String deviceUid;
    private final String type;
    private final String name;

    public DeviceSelfRegisterRequest(String deviceUid, String type, String name) {
        this.deviceUid = deviceUid;
        this.type = type;
        this.name = name;
    }

    public String getDeviceUid() {
        return deviceUid;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
