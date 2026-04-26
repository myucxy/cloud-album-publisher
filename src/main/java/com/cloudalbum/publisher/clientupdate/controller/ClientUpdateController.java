package com.cloudalbum.publisher.clientupdate.controller;

import com.cloudalbum.publisher.clientupdate.dto.ClientDownloadListResponse;
import com.cloudalbum.publisher.clientupdate.dto.ClientUpdateResponse;
import com.cloudalbum.publisher.clientupdate.service.ClientUpdateService;
import com.cloudalbum.publisher.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "客户端更新")
@RestController
@RequestMapping("/api/v1/client-updates")
@RequiredArgsConstructor
public class ClientUpdateController {

    private final ClientUpdateService clientUpdateService;

    @Operation(summary = "检查客户端更新")
    @GetMapping("/check")
    public Result<ClientUpdateResponse> checkUpdate(@RequestParam String platform,
                                                    @RequestParam(required = false) String currentVersion,
                                                    @RequestParam(required = false) Integer currentVersionCode,
                                                    @RequestParam(defaultValue = "stable") String channel) {
        return Result.success(clientUpdateService.checkUpdate(platform, currentVersion, currentVersionCode, channel));
    }

    @Operation(summary = "获取客户端下载列表")
    @GetMapping("/downloads")
    public Result<ClientDownloadListResponse> listDownloads() {
        return Result.success(clientUpdateService.listDownloads());
    }
}
