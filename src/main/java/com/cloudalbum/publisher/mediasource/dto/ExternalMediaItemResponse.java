package com.cloudalbum.publisher.mediasource.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExternalMediaItemResponse {

    private String externalMediaKey;
    private Long sourceId;
    private String sourceType;
    private String sourceName;
    private String path;
    private String filePath;
    private String originUri;
    private String name;
    private String fileName;
    private String folderPath;
    private String contentType;
    private String mediaType;
    private Long size;
    private Long fileSize;
    private LocalDateTime modifiedAt;
    private String ingestMode;
    private String status;
    private String url;
    private String thumbnailUrl;
}
