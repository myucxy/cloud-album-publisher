package com.cloudalbum.publisher.album.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AlbumBatchAddContentRequest {

    @Valid
    @NotEmpty(message = "待添加媒体不能为空")
    private List<AlbumAddContentRequest> items;
}
