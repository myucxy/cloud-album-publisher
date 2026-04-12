package com.cloudalbum.publisher.mediasource.controller;

import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import com.cloudalbum.publisher.media.dto.MediaResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceCreateRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceImportRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceUpdateRequest;
import com.cloudalbum.publisher.mediasource.service.MediaSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "媒体源管理")
@RestController
@RequestMapping("/api/v1/media-sources")
@RequiredArgsConstructor
public class MediaSourceController {

    private final MediaSourceService mediaSourceService;

    @Operation(summary = "获取媒体源列表")
    @GetMapping
    public Result<List<MediaSourceResponse>> listMediaSources() {
        return Result.success(mediaSourceService.listMediaSources(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "创建媒体源")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<MediaSourceResponse> createMediaSource(@Valid @RequestBody MediaSourceCreateRequest request) {
        return Result.success(mediaSourceService.createMediaSource(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "更新媒体源")
    @PatchMapping("/{id}")
    public Result<MediaSourceResponse> updateMediaSource(@PathVariable Long id,
                                                         @Valid @RequestBody MediaSourceUpdateRequest request) {
        return Result.success(mediaSourceService.updateMediaSource(id, SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "删除媒体源")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> deleteMediaSource(@PathVariable Long id) {
        mediaSourceService.deleteMediaSource(id, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "浏览媒体源目录")
    @GetMapping("/{id}/browse")
    public Result<MediaSourceBrowseResponse> browse(@PathVariable Long id,
                                                    @RequestParam(required = false) String path) {
        return Result.success(mediaSourceService.browse(id, SecurityUtil.getCurrentUserId(), path));
    }

    @Operation(summary = "根据临时配置浏览媒体源目录")
    @PostMapping("/browse")
    public Result<MediaSourceBrowseResponse> browse(@RequestBody(required = false) MediaSourceBrowseRequest request) {
        return Result.success(mediaSourceService.browse(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "访问外部媒体原文件")
    @GetMapping("/{id}/content")
    public void getMediaContent(@PathVariable Long id,
                                @RequestParam String path,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        mediaSourceService.writeMediaContent(id, SecurityUtil.getCurrentUserId(), path, false, request, response);
    }

    @Operation(summary = "访问外部媒体缩略图")
    @GetMapping("/{id}/thumbnail")
    public void getMediaThumbnail(@PathVariable Long id,
                                  @RequestParam String path,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        mediaSourceService.writeMediaContent(id, SecurityUtil.getCurrentUserId(), path, true, request, response);
    }

    @Operation(summary = "导入媒体源内容到媒体库")
    @PostMapping("/{id}/import")
    public Result<List<MediaResponse>> importMedia(@PathVariable Long id,
                                                   @RequestBody(required = false) MediaSourceImportRequest request) {
        return Result.success(mediaSourceService.importMedia(id, SecurityUtil.getCurrentUserId(), request));
    }
}
