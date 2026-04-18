package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AlbumBgmOrderRequest {

    @NotEmpty(message = "排序列表不能为空")
    private List<@NotNull(message = "BGM ID 不能为空") Long> ids;
}
