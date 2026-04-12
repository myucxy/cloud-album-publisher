package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumAddContentRequest {

    @NotNull(message = "媒体ID不能为空")
    private Long mediaId;

    private Integer sortOrder = 0;

    /** 展示时长（秒），默认5秒 */
    private Integer duration = 5;
}
