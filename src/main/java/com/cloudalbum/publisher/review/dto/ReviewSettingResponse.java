package com.cloudalbum.publisher.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "审核设置响应")
public class ReviewSettingResponse {

    @Schema(description = "是否自动审核通过")
    private Boolean autoApproveEnabled;
}
