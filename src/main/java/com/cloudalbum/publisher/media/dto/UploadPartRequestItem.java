package com.cloudalbum.publisher.media.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadPartRequestItem {

    @NotNull(message = "分片序号不能为空")
    @Min(value = 1, message = "分片序号最小为1")
    private Integer partNumber;

    @NotBlank(message = "分片etag不能为空")
    private String etag;
}
