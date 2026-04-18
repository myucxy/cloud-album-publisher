package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumBgmRequest {

    private Long mediaId;

    private Long sourceId;

    private String sourceType;

    private String sourceName;

    private String externalMediaKey;

    private String path;

    private String fileName;

    private String contentType;

    private String mediaType;

    private Boolean clear;

    @Min(value = 0, message = "音量最小0")
    @Max(value = 100, message = "音量最大100")
    private Integer bgmVolume = 80;
}
