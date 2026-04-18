package com.cloudalbum.publisher.album.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_album_bgm")
public class AlbumBgm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long albumId;

    private Long mediaId;

    private Long sourceId;

    private String sourceType;

    private String sourceName;

    private String externalMediaKey;

    private String filePath;

    private String fileName;

    private String contentType;

    private String mediaType;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public boolean isExternal() {
        return mediaId == null && externalMediaKey != null && !externalMediaKey.isBlank();
    }
}
