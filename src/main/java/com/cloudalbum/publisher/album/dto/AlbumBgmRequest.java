package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumBgmRequest {

    private String bgmUrl;

    @Min(value = 0, message = "音量最小0")
    @Max(value = 100, message = "音量最大100")
    private Integer bgmVolume = 80;
}
