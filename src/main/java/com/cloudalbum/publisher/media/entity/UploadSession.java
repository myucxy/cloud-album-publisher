package com.cloudalbum.publisher.media.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_upload_session")
public class UploadSession {

    @TableId(type = IdType.INPUT)
    private String uploadId;

    private Long userId;

    private String fileName;

    private String contentType;

    private Long fileSize;

    private Integer totalParts;

    private Integer uploadedParts;

    private String objectKey;

    /** INIT / UPLOADING / COMPLETED / FAILED */
    private String status;

    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
