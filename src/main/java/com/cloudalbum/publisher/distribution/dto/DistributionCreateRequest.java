package com.cloudalbum.publisher.distribution.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "创建分发规则请求")
public class DistributionCreateRequest {

    @Schema(description = "相册ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "相册ID不能为空")
    private Long albumId;

    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称不能超过100个字符")
    private String name;

    @Schema(description = "是否循环播放", defaultValue = "true")
    private Boolean loopPlay = true;

    @Schema(description = "是否随机播放", defaultValue = "false")
    private Boolean shuffle = false;

    @Schema(description = "每张展示时长（秒），最小1秒", defaultValue = "10")
    @Min(value = 1, message = "展示时长不能小于1秒")
    private Integer itemDuration = 10;

    @Schema(description = "关联的设备ID列表")
    private List<Long> deviceIds;

    @Schema(description = "关联的设备组ID列表")
    private List<Long> groupIds;
}
