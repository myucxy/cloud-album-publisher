package com.cloudalbum.publisher.album.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AlbumResponse {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String coverUrl;
    private Long coverMediaId;
    private Long coverSourceId;
    private String coverSourceType;
    private String coverSourceName;
    private String coverExternalMediaKey;
    private String coverPath;
    private String coverFileName;
    private String coverContentType;
    private String coverMediaType;
    private String bgmUrl;
    private Long bgmMediaId;
    private Long bgmSourceId;
    private String bgmSourceType;
    private String bgmSourceName;
    private String bgmExternalMediaKey;
    private String bgmPath;
    private String bgmFileName;
    private String bgmContentType;
    private String bgmMediaType;
    private Integer bgmVolume;
    private String transitionStyle;
    private String visibility;
    private String status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
