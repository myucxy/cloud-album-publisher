package com.cloudalbum.publisher.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "管理端统计数据")
public class AdminStatsResponse {

    @Schema(description = "注册用户总数")
    private long totalUsers;

    @Schema(description = "相册总数")
    private long totalAlbums;

    @Schema(description = "待审核媒体数")
    private long pendingReviews;

    @Schema(description = "当前生效分发规则数")
    private long activeDistributions;
}
