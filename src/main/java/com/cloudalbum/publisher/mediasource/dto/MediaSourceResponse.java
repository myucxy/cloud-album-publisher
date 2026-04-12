package com.cloudalbum.publisher.mediasource.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
public class MediaSourceResponse {

    private Long id;
    private String name;
    private String sourceType;
    private Map<String, Object> config;
    private Map<String, Object> configSummary;
    private String boundPath;
    private String boundPathName;
    private Boolean enabled;
    private Boolean passwordConfigured;
    private LocalDateTime lastScanAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
