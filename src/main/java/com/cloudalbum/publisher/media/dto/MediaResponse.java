package com.cloudalbum.publisher.media.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MediaResponse {

    private Long id;

    private Long userId;

    private String fileName;

    private String contentType;

    private String mediaType;

    private Long fileSize;

    private String sourceType;

    private Long sourceId;

    private String sourceName;

    private String folderPath;

    private String originUri;

    private String ingestMode;

    private String url;

    private String thumbnailUrl;

    private Integer durationSec;

    private Integer width;

    private Integer height;

    private String status;

    private String reviewStatus;

    private String reviewRejectReason;

    private LocalDateTime reviewedAt;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
