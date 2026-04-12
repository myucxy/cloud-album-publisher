package com.cloudalbum.publisher.media.controller;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import com.cloudalbum.publisher.media.dto.*;
import com.cloudalbum.publisher.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "媒体管理")
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "获取媒体列表")
    @GetMapping
    public Result<PageResult<MediaResponse>> listMedia(@Valid PageRequest pageRequest,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String mediaType,
                                                       @RequestParam(required = false) String sourceType,
                                                       @RequestParam(required = false) Long sourceId,
                                                       @RequestParam(required = false) String folderPath,
                                                       @RequestParam(required = false) String keyword) {
        return Result.success(mediaService.listMedia(
                SecurityUtil.getCurrentUserId(),
                pageRequest,
                status,
                mediaType,
                sourceType,
                sourceId,
                folderPath,
                keyword));
    }

    @Operation(summary = "获取媒体分组")
    @GetMapping("/groups")
    public Result<MediaLibraryGroupsResponse> getMediaGroups(@RequestParam(required = false) String keyword) {
        return Result.success(mediaService.getMediaGroups(SecurityUtil.getCurrentUserId(), keyword));
    }

    @Operation(summary = "初始化分片上传")
    @PostMapping("/upload/init")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<MediaUploadInitResponse> initUpload(@Valid @RequestBody MediaUploadInitRequest request) {
        return Result.success(mediaService.initUpload(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "上传分片")
    @PutMapping("/upload/{uploadId}/{partNumber}")
    public Result<MediaUploadPartResponse> uploadPart(@PathVariable String uploadId,
                                                      @PathVariable Integer partNumber,
                                                      @RequestPart("file") MultipartFile file) {
        return Result.success(mediaService.uploadPart(SecurityUtil.getCurrentUserId(), uploadId, partNumber, file));
    }

    @Operation(summary = "完成上传并触发处理")
    @PostMapping("/upload/{uploadId}/complete")
    public Result<MediaResponse> completeUpload(@PathVariable String uploadId,
                                                @RequestBody(required = false) MediaUploadCompleteRequest request) {
        return Result.success(mediaService.completeUpload(SecurityUtil.getCurrentUserId(), uploadId, request));
    }

    @Operation(summary = "查询上传状态")
    @GetMapping("/upload-status/{uploadId}")
    public Result<MediaUploadStatusResponse> getUploadStatus(@PathVariable String uploadId) {
        return Result.success(mediaService.getUploadStatus(SecurityUtil.getCurrentUserId(), uploadId));
    }

    @Operation(summary = "获取媒体详情")
    @GetMapping("/{id}")
    public Result<MediaResponse> getMedia(@PathVariable Long id) {
        return Result.success(mediaService.getMedia(id, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "访问媒体原文件")
    @GetMapping("/{id}/content")
    public void getMediaContent(@PathVariable Long id,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        mediaService.writeMediaContent(id, SecurityUtil.getCurrentUserId(), false, request, response);
    }

    @Operation(summary = "访问媒体缩略图")
    @GetMapping("/{id}/thumbnail")
    public void getMediaThumbnail(@PathVariable Long id,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        mediaService.writeMediaContent(id, SecurityUtil.getCurrentUserId(), true, request, response);
    }

    @Operation(summary = "删除媒体")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> deleteMedia(@PathVariable Long id) {
        mediaService.deleteMedia(id, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "触发媒体处理")
    @PostMapping("/{id}/process")
    public Result<Void> triggerProcess(@PathVariable Long id) {
        mediaService.triggerProcess(id, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "查询媒体处理状态")
    @GetMapping("/{id}/status")
    public Result<MediaStatusResponse> getMediaStatus(@PathVariable Long id) {
        return Result.success(mediaService.getMediaStatus(id, SecurityUtil.getCurrentUserId()));
    }
}
