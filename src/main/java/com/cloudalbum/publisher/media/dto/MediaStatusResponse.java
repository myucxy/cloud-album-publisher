package com.cloudalbum.publisher.media.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MediaStatusResponse {

    private Long mediaId;

    private String mediaStatus;

    private String taskStatus;

    private String errorMessage;

    private LocalDateTime updatedAt;
}
