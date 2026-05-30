package com.cloudalbum.publisher.focalpoint.controller;

import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import com.cloudalbum.publisher.focalpoint.dto.*;
import com.cloudalbum.publisher.focalpoint.service.FocalPointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "焦点检测管理")
@RestController
@RequestMapping("/api/v1/focal-point")
@RequiredArgsConstructor
public class FocalPointController {

    private final FocalPointService focalPointService;

    @Operation(summary = "更新相册焦点设置")
    @PatchMapping("/albums/{albumId}/settings")
    public Result<Void> updateAlbumSettings(@PathVariable Long albumId,
                                            @Valid @RequestBody AlbumFocalPointSettingsRequest request) {
        focalPointService.updateAlbumSettings(albumId, SecurityUtil.getCurrentUserId(), request);
        return Result.success();
    }

    @Operation(summary = "手动设置焦点坐标")
    @PutMapping("/albums/{albumId}/contents/{contentId}/focal-point")
    public Result<Void> updateFocalPoint(@PathVariable Long albumId,
                                         @PathVariable Long contentId,
                                         @Valid @RequestBody FocalPointUpdateRequest request) {
        focalPointService.updateFocalPoint(albumId, contentId, SecurityUtil.getCurrentUserId(), request);
        return Result.success();
    }

    @Operation(summary = "清除焦点数据")
    @DeleteMapping("/albums/{albumId}/contents/{contentId}/focal-point")
    public Result<Void> clearFocalPoint(@PathVariable Long albumId,
                                        @PathVariable Long contentId) {
        focalPointService.clearFocalPoint(albumId, contentId, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "批量处理焦点检测")
    @PostMapping("/albums/{albumId}/process")
    public Result<FocalPointProcessResult> batchProcess(@PathVariable Long albumId,
                                                        @Valid @RequestBody FocalPointBatchProcessRequest request) {
        return Result.success(focalPointService.batchProcess(albumId, SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "获取可用 Provider 列表")
    @GetMapping("/providers")
    public Result<List<String>> getAvailableProviders() {
        return Result.success(focalPointService.getAvailableProviderTypes());
    }

    @Operation(summary = "获取 LLM 配置列表")
    @GetMapping("/llm-configs")
    public Result<List<VisionLlmConfigResponse>> listLlmConfigs() {
        return Result.success(focalPointService.listLlmConfigs(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "获取 LLM 配置详情")
    @GetMapping("/llm-configs/{id}")
    public Result<VisionLlmConfigResponse> getLlmConfig(@PathVariable Long id) {
        return Result.success(focalPointService.getLlmConfig(id, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "新建 LLM 配置")
    @PostMapping("/llm-configs")
    public Result<VisionLlmConfigResponse> createLlmConfig(@Valid @RequestBody VisionLlmConfigRequest request) {
        return Result.success(focalPointService.createLlmConfig(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "更新 LLM 配置")
    @PutMapping("/llm-configs/{id}")
    public Result<VisionLlmConfigResponse> updateLlmConfig(@PathVariable Long id,
                                                           @Valid @RequestBody VisionLlmConfigRequest request) {
        return Result.success(focalPointService.updateLlmConfig(id, SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "删除 LLM 配置")
    @DeleteMapping("/llm-configs/{id}")
    public Result<Void> deleteLlmConfig(@PathVariable Long id) {
        focalPointService.deleteLlmConfig(id, SecurityUtil.getCurrentUserId());
        return Result.success();
    }
}
