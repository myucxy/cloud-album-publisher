package com.cloudalbum.publisher.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceGroupCreateRequest {

    @NotBlank(message = "分组名称不能为空")
    private String name;

    private String description;
}
