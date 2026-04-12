package com.cloudalbum.publisher.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "审核设置更新请求")
public class ReviewSettingUpdateRequest {

    @NotNull(message = "自动审核通过设置不能为空")
    @Schema(description = "是否自动审核通过", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean autoApproveEnabled;
}
