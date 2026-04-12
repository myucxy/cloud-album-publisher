package com.cloudalbum.publisher.media.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaUploadPartResponse {

    private String uploadId;

    private Integer partNumber;

    private String etag;

    private Integer uploadedParts;

    private Integer totalParts;
}
