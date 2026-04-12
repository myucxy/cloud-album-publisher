package com.cloudalbum.publisher.device.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DeviceGroupResponse {

    private Long id;

    private String name;

    private String description;

    private Long deviceCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
