package com.cloudalbum.publisher.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_device_group_rel")
public class DeviceGroupRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long deviceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
