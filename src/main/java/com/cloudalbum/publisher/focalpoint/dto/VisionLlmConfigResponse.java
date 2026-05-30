package com.cloudalbum.publisher.focalpoint.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class VisionLlmConfigResponse {

    private Long id;
    private String name;
    private String apiEndpoint;
    private String apiKeyMasked;
    private String modelName;
    private Integer maxTokens;
    private Integer timeoutSeconds;
    private String extraParams;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
