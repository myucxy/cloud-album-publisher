package com.cloudalbum.publisher.device.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DeviceResponse {

    private Long id;

    private String deviceUid;

    private String name;

    private String type;

    private String status;

    private LocalDateTime lastHeartbeatAt;

    private LocalDateTime boundAt;

    private LocalDateTime updatedAt;
}
