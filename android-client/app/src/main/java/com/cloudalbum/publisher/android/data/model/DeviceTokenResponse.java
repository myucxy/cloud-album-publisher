package com.cloudalbum.publisher.android.data.model;

public class DeviceTokenResponse {
    private String accessToken;
    private long accessTokenExpire;
    private long deviceId;
    private String deviceUid;

    public String getAccessToken() {
        return accessToken;
    }

    public long getAccessTokenExpire() {
        return accessTokenExpire;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public String getDeviceUid() {
        return deviceUid;
    }
}
