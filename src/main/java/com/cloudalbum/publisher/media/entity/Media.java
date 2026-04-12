package com.cloudalbum.publisher.media.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_media")
public class Media {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fileName;

    private String contentType;

    /** IMAGE / VIDEO / AUDIO / OTHER */
    private String mediaType;

    private Long fileSize;

    /** UPLOAD / SMB / WEBDAV / NAS */
    private String sourceType;

    private Long sourceId;

    private String sourceName;

    private String folderPath;

    private String originUri;

    /** UPLOADED / LINKED / CACHED */
    private String ingestMode;

    private String bucketName;

    private String objectKey;

    private String thumbnailKey;

    private Integer durationSec;

    private Integer width;

    private Integer height;

    /** UPLOADING / UPLOADED / PROCESSING / READY / FAILED */
    private String status;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
