package com.cloudalbum.publisher.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "审核驳回请求")
public class ReviewRejectRequest {

    @Schema(description = "驳回原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 500, message = "驳回原因不能超过500个字符")
    private String rejectReason;
}
