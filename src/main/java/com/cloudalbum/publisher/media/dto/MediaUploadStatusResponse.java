package com.cloudalbum.publisher.media.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MediaUploadStatusResponse {

    private String uploadId;

    private String status;

    private Integer uploadedParts;

    private Integer totalParts;

    private Integer progress;

    private List<Integer> missingParts;
}
