package com.cloudalbum.publisher.album.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
public class AlbumCoverRequest {

    private Long mediaId;

    private Long sourceId;

    private String sourceType;

    private String sourceName;

    private String externalMediaKey;

    private String path;

    private String fileName;

    private String contentType;

    private String mediaType;

    public boolean isExternal() {
        return StringUtils.hasText(externalMediaKey);
    }

    public boolean isInternal() {
        return mediaId != null;
    }
}
