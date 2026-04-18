package com.cloudalbum.publisher.album.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumContentResponse {

    private Long id;
    private Long mediaId;
    private String externalMediaKey;
    private Long sourceId;
    private String sourceType;
    private String sourceName;
    private String path;
    private String fileName;
    private String contentType;
    private String mediaType;
    private String url;
    private String thumbnailUrl;
    private Integer sortOrder;
    private Integer duration;
}
