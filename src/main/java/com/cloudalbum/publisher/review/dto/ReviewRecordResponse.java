package com.cloudalbum.publisher.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "审核记录响应")
public class ReviewRecordResponse {

    @Schema(description = "审核记录ID")
    private Long id;

    @Schema(description = "媒体ID")
    private Long mediaId;

    @Schema(description = "上传者用户ID")
    private Long userId;

    @Schema(description = "审核人用户ID")
    private Long reviewerId;

    @Schema(description = "审核状态：PENDING / APPROVED / REJECTED")
    private String status;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "审核时间")
    private LocalDateTime reviewedAt;

    @Schema(description = "提交时间")
    private LocalDateTime createdAt;
}
