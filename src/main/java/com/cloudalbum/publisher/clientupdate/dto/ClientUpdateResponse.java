package com.cloudalbum.publisher.clientupdate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "客户端更新检测响应")
public class ClientUpdateResponse {

    @Schema(description = "客户端平台")
    private String platform;

    @Schema(description = "发布通道")
    private String channel;

    @Schema(description = "当前版本号")
    private String currentVersion;

    @Schema(description = "当前版本序号")
    private Integer currentVersionCode;

    @Schema(description = "最新版本号")
    private String latestVersion;

    @Schema(description = "最新版本序号")
    private Integer latestVersionCode;

    @Schema(description = "是否存在更新")
    private Boolean hasUpdate;

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
