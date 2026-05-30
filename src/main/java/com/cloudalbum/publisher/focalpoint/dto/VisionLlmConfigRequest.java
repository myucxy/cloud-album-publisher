package com.cloudalbum.publisher.focalpoint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VisionLlmConfigRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String apiEndpoint;

    private String apiKey;

    @NotBlank
    private String modelName;

    private Integer maxTokens = 1024;

    private Integer timeoutSeconds = 30;

    private String extraParams;

    private Boolean enabled = true;
}
