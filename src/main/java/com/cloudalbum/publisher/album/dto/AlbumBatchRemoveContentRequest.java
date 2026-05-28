package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AlbumBatchRemoveContentRequest {

    @NotEmpty(message = "待移除媒体不能为空")
    private List<Long> contentIds;
}
