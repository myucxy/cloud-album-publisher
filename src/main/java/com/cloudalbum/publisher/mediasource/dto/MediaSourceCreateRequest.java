package com.cloudalbum.publisher.mediasource.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class MediaSourceCreateRequest {

    @NotBlank(message = "媒体源名称不能为空")
    private String name;

    @NotBlank(message = "媒体源类型不能为空")
    private String sourceType;

    @NotBlank(message = "绑定目录不能为空")
    private String boundPath;

    private String boundPathName;

    private Boolean enabled;

    private Map<String, Object> config;

    private Map<String, Object> credentials;
}
