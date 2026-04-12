package com.cloudalbum.publisher.mediasource.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MediaSourceBrowseItemResponse {

    private String externalMediaKey;
    private Long sourceId;
    private String sourceType;
    private String sourceName;
    private String path;
    private String originUri;
    private String name;
    private String fileName;
    private Boolean directory;
    private Long size;
    private Long fileSize;
    private String folderPath;
    private LocalDateTime modifiedAt;
    private String contentType;
    private String mediaType;
    private String ingestMode;
    private String status;
    private String url;
    private String thumbnailUrl;
    private Boolean hasChildren;
}
