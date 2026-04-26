package com.cloudalbum.publisher.clientupdate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Schema(description = "客户端下载列表响应")
public class ClientDownloadListResponse {

    @Schema(description = "平台下载列表")
    private List<PlatformDownloads> platforms = new ArrayList<>();

    @Getter
    @Setter
    @Schema(description = "平台下载项")
    public static class PlatformDownloads {

        @Schema(description = "客户端平台")
        private String platform;

        @Schema(description = "通道下载列表")
        private List<DownloadItem> channels = new ArrayList<>();
    }

    @Getter
    @Setter
    @Schema(description = "客户端下载项")
    public static class DownloadItem {

        @Schema(description = "发布通道")
        private String channel;

        @Schema(description = "版本号")
        private String version;

        @Schema(description = "版本序号")
        private Integer versionCode;

        @Schema(description = "是否强制更新")
        private Boolean forceUpdate;

        @Schema(description = "下载地址")
        private String downloadUrl;

        @Schema(description = "文件名")
        private String fileName;

        @Schema(description = "SHA-256 校验值")
        private String sha256;

        @Schema(description = "文件大小")
        private Long size;

        @Schema(description = "更新说明")
        private String releaseNotes;

        @Schema(description = "发布时间")
        private String publishedAt;
    }
}
