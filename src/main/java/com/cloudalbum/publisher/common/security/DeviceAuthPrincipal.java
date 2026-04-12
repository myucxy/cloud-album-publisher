package com.cloudalbum.publisher.common.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeviceAuthPrincipal {

    private final Long userId;
    private final Long deviceId;
    private final String deviceUid;
}
