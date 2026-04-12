package com.cloudalbum.publisher.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceStatusUpdateRequest {

    @NotBlank(message = "设备状态不能为空")
    private String status;
}
