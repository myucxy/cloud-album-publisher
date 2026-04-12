package com.cloudalbum.publisher.distribution.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "更新分发规则请求")
public class DistributionUpdateRequest {

    @Schema(description = "规则名称")
    @Size(max = 100, message = "规则名称不能超过100个字符")
    private String name;

    @Schema(description = "是否循环播放")
    private Boolean loopPlay;

    @Schema(description = "是否随机播放")
    private Boolean shuffle;

    @Schema(description = "每张展示时长（秒）")
    @Min(value = 1, message = "展示时长不能小于1秒")
    private Integer itemDuration;

    @Schema(description = "关联的设备ID列表（全量替换）")
    private List<Long> deviceIds;

    @Schema(description = "关联的设备组ID列表（全量替换）")
    private List<Long> groupIds;
}
