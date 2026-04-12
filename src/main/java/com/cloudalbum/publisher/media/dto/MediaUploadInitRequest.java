package com.cloudalbum.publisher.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaUploadInitRequest {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotBlank(message = "内容类型不能为空")
    private String contentType;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于0")
    private Long fileSize;
}
