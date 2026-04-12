package com.cloudalbum.publisher.media.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaUploadInitResponse {

    private String uploadId;

    private String objectKey;

    private Long partSize;

    private Integer totalParts;
}
