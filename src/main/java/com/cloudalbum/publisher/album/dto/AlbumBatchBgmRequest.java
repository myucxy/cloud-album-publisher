package com.cloudalbum.publisher.album.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AlbumBatchBgmRequest {

    @Valid
    @NotEmpty(message = "待添加BGM不能为空")
    private List<AlbumBgmRequest> items;
}
