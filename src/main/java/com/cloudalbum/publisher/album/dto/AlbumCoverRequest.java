package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumCoverRequest {

    @NotNull(message = "封面媒体不能为空")
    private Long mediaId;
}
