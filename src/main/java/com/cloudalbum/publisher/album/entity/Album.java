package com.cloudalbum.publisher.album.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_album")
public class Album {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String coverUrl;

    private Long coverMediaId;

    private String bgmUrl;

    /** 音量 0-100 */
    private Integer bgmVolume;

    /** PUBLIC / PRIVATE / DEVICE_ONLY */
    private String visibility;

    /** DRAFT / PUBLISHED */
    private String status;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
