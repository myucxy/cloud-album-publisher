package com.cloudalbum.publisher.album.controller;

import com.cloudalbum.publisher.album.dto.*;
import com.cloudalbum.publisher.album.service.AlbumService;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "相册管理")
@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @Operation(summary = "获取我的相册列表")
    @GetMapping
    public Result<PageResult<AlbumResponse>> listAlbums(@Valid PageRequest pageRequest,
                                                        @RequestParam(required = false) String visibility) {
        return Result.success(albumService.listAlbums(SecurityUtil.getCurrentUserId(), pageRequest, visibility));
    }

    @Operation(summary = "获取相册详情")
    @GetMapping("/{id}")
    public Result<AlbumResponse> getAlbum(@PathVariable Long id) {
        return Result.success(albumService.getAlbum(id, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "访问相册封面")
    @GetMapping("/{id}/cover")
    public void getAlbumCover(@PathVariable Long id,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        albumService.writeAlbumCover(id, SecurityUtil.getCurrentUserId(), request, response);
    }

    @Operation(summary = "创建相册")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AlbumResponse> createAlbum(@Valid @RequestBody AlbumCreateRequest request) {
        return Result.success(albumService.createAlbum(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "更新相册信息")
    @PutMapping("/{id}")
    public Result<AlbumResponse> updateAlbum(@PathVariable Long id,
                                             @Valid @RequestBody AlbumUpdateRequest request) {
        return Result.success(albumService.updateAlbum(id, SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "删除相册")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> deleteAlbum(@PathVariable Long id) {
        albumService.deleteAlbum(id, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "获取相册内容列表")
    @GetMapping("/{id}/contents")
    public Result<PageResult<AlbumContentResponse>> listContents(@PathVariable Long id,
                                                                 @Valid PageRequest pageRequest) {
        return Result.success(albumService.listContents(id, SecurityUtil.getCurrentUserId(), pageRequest));
    }

    @Operation(summary = "向相册添加媒体")
    @PostMapping("/{id}/contents")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AlbumContentResponse> addContent(@PathVariable Long id,
                                                   @Valid @RequestBody AlbumAddContentRequest request) {
        return Result.success(albumService.addContent(id, SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "移除相册中的媒体")
    @DeleteMapping("/{id}/contents/{contentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> removeContent(@PathVariable Long id, @PathVariable Long contentId) {
        albumService.removeContent(id, contentId, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "更新相册封面")
    @PatchMapping("/{id}/cover")
    public Result<AlbumResponse> updateCover(@PathVariable Long id,
                                             @Valid @RequestBody AlbumCoverRequest request) {
        return Result.success(albumService.updateCover(id, SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "设置相册BGM")
    @PatchMapping("/{id}/bgm")
    public Result<AlbumResponse> updateBgm(@PathVariable Long id,
                                           @Valid @RequestBody AlbumBgmRequest request) {
        return Result.success(albumService.updateBgm(id, SecurityUtil.getCurrentUserId(), request));
    }
}
