package com.cloudalbum.publisher.album.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlbumCreateRequest {

    @NotBlank(message = "相册名称不能为空")
    @Size(max = 200, message = "相册名称最长200位")
    private String title;

    private String description;

    /** PUBLIC / PRIVATE / DEVICE_ONLY，默认PRIVATE */
    private String visibility = "PRIVATE";
}
