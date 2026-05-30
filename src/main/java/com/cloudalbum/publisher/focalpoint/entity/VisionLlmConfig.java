package com.cloudalbum.publisher.focalpoint.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_vision_llm_config")
public class VisionLlmConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String apiEndpoint;

    private String apiKeyEncrypted;

    private String modelName;

    private Integer maxTokens;

    private Integer timeoutSeconds;

    private String extraParams;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
