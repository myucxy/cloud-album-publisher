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
    private String bgmUrl;
    private Integer bgmVolume;
    private String visibility;
    private String status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
