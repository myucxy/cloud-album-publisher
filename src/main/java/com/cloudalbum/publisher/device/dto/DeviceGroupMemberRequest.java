package com.cloudalbum.publisher.device.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceGroupMemberRequest {

    @NotNull(message = "设备ID不能为空")
    private Long deviceId;
}
