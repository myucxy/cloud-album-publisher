package com.cloudalbum.publisher.distribution.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_distribution")
public class Distribution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long albumId;

    private Long userId;

    private String name;

    /** 是否循环播放 */
    private Boolean loopPlay;

    /** 是否随机播放 */
    private Boolean shuffle;

    /** 每张展示时长（秒） */
    private Integer itemDuration;

    /** 状态：DRAFT / ACTIVE / DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
