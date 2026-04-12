package com.cloudalbum.publisher.distribution.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "分发规则响应")
public class DistributionResponse {

    @Schema(description = "规则ID")
    private Long id;

    @Schema(description = "相册ID")
    private Long albumId;

    @Schema(description = "规则名称")
    private String name;

    @Schema(description = "是否循环播放")
    private Boolean loopPlay;

    @Schema(description = "是否随机播放")
    private Boolean shuffle;

    @Schema(description = "每张展示时长（秒）")
    private Integer itemDuration;

    @Schema(description = "状态：DRAFT / ACTIVE / DISABLED")
    private String status;

    @Schema(description = "关联设备ID列表")
    private List<Long> deviceIds;

    @Schema(description = "关联设备组ID列表")
    private List<Long> groupIds;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
