package com.cloudalbum.publisher.mediasource.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class MediaSourceBrowseRequest {

    private Long sourceId;

    private String sourceType;

    private String name;

    private String path;

    private Map<String, Object> config;

    private Map<String, Object> credentials;
}
