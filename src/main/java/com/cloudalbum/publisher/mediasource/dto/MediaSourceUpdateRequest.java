package com.cloudalbum.publisher.mediasource.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class MediaSourceUpdateRequest {

    private String name;

    private String boundPath;

    private String boundPathName;

    private Boolean enabled;

    private Map<String, Object> config;

    private Map<String, Object> credentials;
}
