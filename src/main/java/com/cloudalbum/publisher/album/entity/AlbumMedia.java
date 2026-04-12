package com.cloudalbum.publisher.album.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_album_media")
public class AlbumMedia {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long albumId;

    /** 关联 t_media，由模块B维护 */
    private Long mediaId;

    private Integer sortOrder;

    /** 展示时长（秒） */
    private Integer duration;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
