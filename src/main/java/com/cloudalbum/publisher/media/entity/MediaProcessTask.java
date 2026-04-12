package com.cloudalbum.publisher.media.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_media_process_task")
public class MediaProcessTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;

    private Long userId;

    /** MEDIA_PROCESS */
    private String taskType;

    /** PENDING / RUNNING / SUCCESS / FAILED / RETRY_WAIT */
    private String status;

    private Integer retryCount;

    private Integer maxRetry;

    private LocalDateTime nextRunAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
