package com.cloudalbum.publisher.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceTokenRequest {

    @NotBlank(message = "设备唯一ID不能为空")
    private String deviceUid;
}
