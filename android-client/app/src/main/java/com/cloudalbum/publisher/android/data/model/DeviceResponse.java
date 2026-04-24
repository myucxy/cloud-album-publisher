package com.cloudalbum.publisher.android.data.model;

public class DeviceResponse {
    private long id;
    private String deviceUid;
    private String name;
    private String type;
    private String status;
    private String lastHeartbeatAt;
    private String boundAt;
    private String updatedAt;

    public long getId() {
        return id;
    }

    public String getDeviceUid() {
        return deviceUid;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public String getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public String getBoundAt() {
        return boundAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
