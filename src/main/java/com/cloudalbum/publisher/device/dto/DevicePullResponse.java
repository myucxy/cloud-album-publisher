package com.cloudalbum.publisher.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Schema(description = "设备拉取内容响应")
public class DevicePullResponse {

    @Schema(description = "设备信息")
    private DeviceResponse device;

    @Schema(description = "当前设备可拉取的分发规则列表")
    private List<DistributionItem> distributions;

    @Schema(description = "拉取时间")
    private LocalDateTime pulledAt;

    @Getter
    @Setter
    @Schema(description = "设备拉取到的分发规则")
    public static class DistributionItem {

        @Schema(description = "规则ID")
        private Long id;

        @Schema(description = "规则名称")
        private String name;

        @Schema(description = "规则状态")
        private String status;

        @Schema(description = "是否循环播放")
        private Boolean loopPlay;

        @Schema(description = "是否随机播放")
        private Boolean shuffle;

        @Schema(description = "规则默认单项展示时长（秒）")
        private Integer itemDuration;

        @Schema(description = "关联相册")
        private AlbumItem album;

        @Schema(description = "可播放媒体列表")
        private List<MediaItem> mediaList;
    }

    @Getter
    @Setter
    @Schema(description = "设备拉取到的相册信息")
    public static class AlbumItem {

        @Schema(description = "相册ID")
        private Long id;

        @Schema(description = "相册标题")
        private String title;

        @Schema(description = "相册描述")
        private String description;

        @Schema(description = "封面地址")
        private String coverUrl;

        @Schema(description = "背景音乐地址")
        private String bgmUrl;

        @Schema(description = "背景音乐音量")
        private Integer bgmVolume;

        @Schema(description = "可见性")
        private String visibility;
    }

    @Getter
    @Setter
    @Schema(description = "设备拉取到的媒体项")
    public static class MediaItem {

        @Schema(description = "媒体ID")
        private Long id;

        @Schema(description = "文件名")
        private String fileName;

        @Schema(description = "媒体类型")
        private String mediaType;

        @Schema(description = "MIME 类型")
        private String contentType;

        @Schema(description = "原始文件地址")
        private String url;

        @Schema(description = "缩略图地址")
        private String thumbnailUrl;

        @Schema(description = "时长（秒）")
        private Integer durationSec;

        @Schema(description = "宽度")
        private Integer width;

        @Schema(description = "高度")
        private Integer height;

        @Schema(description = "在相册中的排序")
        private Integer sortOrder;

        @Schema(description = "最终展示时长（秒）")
        private Integer itemDuration;
    }
}
