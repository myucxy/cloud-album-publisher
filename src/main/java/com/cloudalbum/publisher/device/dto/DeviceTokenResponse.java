package com.cloudalbum.publisher.device.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeviceTokenResponse {

    private String accessToken;
    private long accessTokenExpire;
    private Long deviceId;
    private String deviceUid;
}
