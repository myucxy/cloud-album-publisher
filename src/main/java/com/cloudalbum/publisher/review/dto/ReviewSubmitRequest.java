package com.cloudalbum.publisher.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "提交审核请求（由媒体上传完成后调用）")
public class ReviewSubmitRequest {

    @Schema(description = "媒体ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long mediaId;

    @Schema(description = "上传者用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
}
