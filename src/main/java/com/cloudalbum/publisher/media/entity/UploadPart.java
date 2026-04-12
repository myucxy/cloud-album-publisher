package com.cloudalbum.publisher.media.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_upload_part")
public class UploadPart {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uploadId;

    private Integer partNumber;

    private String etag;

    private Long partSize;

    private String partObjectKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
