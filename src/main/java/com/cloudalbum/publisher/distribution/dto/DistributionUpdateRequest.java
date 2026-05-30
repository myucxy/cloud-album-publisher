package com.cloudalbum.publisher.distribution.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
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

    @Schema(description = "播放转场覆盖，空表示使用相册设置")
    private String transitionStyle;

    @Schema(description = "展示布局覆盖，空表示使用相册设置")
    private String displayStyle;

    @Schema(description = "展示布局子样式覆盖，空表示使用相册设置")
    private String displayVariant;

    @Schema(description = "是否显示时间日期覆盖，空表示使用相册设置")
    private Boolean showTimeAndDate;

    @Getter
    private boolean transitionStylePresent;

    @Getter
    private boolean displayStylePresent;

    @Getter
    private boolean displayVariantPresent;

    @Getter
    private boolean showTimeAndDatePresent;

    @Schema(description = "关联的设备ID列表（全量替换）")
    private List<Long> deviceIds;

    @Schema(description = "关联的设备组ID列表（全量替换）")
    private List<Long> groupIds;

    @JsonSetter("transitionStyle")
    public void setTransitionStyle(String transitionStyle) {
        this.transitionStyle = transitionStyle;
        this.transitionStylePresent = true;
    }

    @JsonSetter("displayStyle")
    public void setDisplayStyle(String displayStyle) {
        this.displayStyle = displayStyle;
        this.displayStylePresent = true;
    }

    @JsonSetter("displayVariant")
    public void setDisplayVariant(String displayVariant) {
        this.displayVariant = displayVariant;
        this.displayVariantPresent = true;
    }

    @JsonSetter("showTimeAndDate")
    public void setShowTimeAndDate(Boolean showTimeAndDate) {
        this.showTimeAndDate = showTimeAndDate;
        this.showTimeAndDatePresent = true;
    }
}
