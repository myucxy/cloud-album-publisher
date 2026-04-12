package com.cloudalbum.publisher.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceRenameRequest {

    @NotBlank(message = "设备名称不能为空")
    private String name;
}
