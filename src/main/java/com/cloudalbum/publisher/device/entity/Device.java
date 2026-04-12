package com.cloudalbum.publisher.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String deviceUid;

    private String name;

    private String type;

    /** UNBOUND / OFFLINE / ONLINE */
    private String status;

    private LocalDateTime lastHeartbeatAt;

    private LocalDateTime boundAt;

    private LocalDateTime unboundAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
