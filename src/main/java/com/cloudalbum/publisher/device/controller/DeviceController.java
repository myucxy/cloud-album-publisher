package com.cloudalbum.publisher.device.controller;

import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.security.DeviceAuthPrincipal;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import com.cloudalbum.publisher.device.dto.*;
import com.cloudalbum.publisher.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "设备管理")
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "获取设备列表")
    @GetMapping
    public Result<List<DeviceResponse>> listDevices() {
        return Result.success(deviceService.listDevices(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "获取未绑定设备列表")
    @GetMapping("/unbound")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<DeviceResponse>> listUnboundDevices() {
        return Result.success(deviceService.listUnboundDevices());
    }

    @Operation(summary = "设备自注册为未绑定状态")
    @PostMapping("/self/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<DeviceResponse> selfRegister(@Valid @RequestBody DeviceSelfRegisterRequest request) {
        return Result.success(deviceService.registerUnboundDevice(request));
    }

    @Operation(summary = "绑定设备")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<DeviceResponse> bindDevice(@Valid @RequestBody DeviceBindRequest request) {
        return Result.success(deviceService.bindDevice(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "绑定未绑定设备")
    @PatchMapping("/{id}/bind")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceResponse> bindUnboundDevice(@PathVariable Long id,
                                                    @Valid @RequestBody AdminDeviceBindRequest request) {
        return Result.success(deviceService.bindUnboundDevice(SecurityUtil.getCurrentUserId(), id, request));
    }

    @Operation(summary = "设备自助获取访问令牌")
    @PostMapping("/self/token")
    public Result<DeviceTokenResponse> createSelfAccessToken(@Valid @RequestBody DeviceTokenRequest request) {
        return Result.success(deviceService.createAccessTokenForCurrentDevice(request));
    }

    @Operation(summary = "为设备签发访问令牌")
    @PostMapping("/token")
    public Result<DeviceTokenResponse> createAccessToken(@Valid @RequestBody DeviceTokenRequest request) {
        return Result.success(deviceService.createAccessToken(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "解绑设备")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> unbindDevice(@PathVariable Long id) {
        deviceService.unbindDevice(SecurityUtil.getCurrentUserId(), id);
        return Result.success();
    }

    @Operation(summary = "修改设备名称")
    @PatchMapping("/{id}/name")
    public Result<DeviceResponse> renameDevice(@PathVariable Long id,
                                               @Valid @RequestBody DeviceRenameRequest request) {
        return Result.success(deviceService.renameDevice(SecurityUtil.getCurrentUserId(), id, request));
    }

    @Operation(summary = "更新设备状态/心跳")
    @PatchMapping("/{id}/status")
    public Result<DeviceResponse> updateDeviceStatus(@PathVariable Long id,
                                                     @Valid @RequestBody DeviceStatusUpdateRequest request) {
        return Result.success(deviceService.updateDeviceStatus(SecurityUtil.getCurrentUserId(), id, request));
    }

    @Operation(summary = "用户按绑定关系拉取设备内容")
    @GetMapping("/pull")
    public Result<DevicePullResponse> pullContent(@RequestParam String deviceUid) {
        return Result.success(deviceService.pullContent(SecurityUtil.getCurrentUserId(), deviceUid));
    }

    @Operation(summary = "设备使用访问令牌拉取内容")
    @GetMapping("/pull/current")
    public Result<DevicePullResponse> pullCurrentDeviceContent() {
        DeviceAuthPrincipal principal = SecurityUtil.getCurrentDevicePrincipal();
        return Result.success(deviceService.pullContentByDevice(principal.getDeviceId()));
    }

    @Operation(summary = "设备访问相册封面")
    @GetMapping("/albums/{albumId}/cover")
    public void getDeviceAlbumCover(@PathVariable Long albumId,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        DeviceAuthPrincipal principal = SecurityUtil.getCurrentDevicePrincipal();
        deviceService.writeDeviceAlbumCover(principal.getDeviceId(), albumId, request, response);
    }

    @Operation(summary = "设备访问相册BGM")
    @GetMapping("/albums/{albumId}/bgm")
    public void getDeviceAlbumBgm(@PathVariable Long albumId,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        DeviceAuthPrincipal principal = SecurityUtil.getCurrentDevicePrincipal();
        deviceService.writeDeviceAlbumBgm(principal.getDeviceId(), albumId, request, response);
    }

    @Operation(summary = "设备访问媒体原文件")
    @GetMapping("/media/{mediaId}/content")
    public void getDeviceMediaContent(@PathVariable Long mediaId,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        DeviceAuthPrincipal principal = SecurityUtil.getCurrentDevicePrincipal();
        deviceService.writeDeviceMediaContent(principal.getDeviceId(), mediaId, false, request, response);
    }

    @Operation(summary = "设备访问媒体缩略图")
    @GetMapping("/media/{mediaId}/thumbnail")
    public void getDeviceMediaThumbnail(@PathVariable Long mediaId,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        DeviceAuthPrincipal principal = SecurityUtil.getCurrentDevicePrincipal();
        deviceService.writeDeviceMediaContent(principal.getDeviceId(), mediaId, true, request, response);
    }

    @Operation(summary = "设备访问外部媒体原文件")
    @GetMapping("/media-sources/{sourceId}/content")
    public void getDeviceExternalMediaContent(@PathVariable Long sourceId,
                                              @RequestParam String path,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        DeviceAuthPrincipal principal = SecurityUtil.getCurrentDevicePrincipal();
        deviceService.writeDeviceExternalMediaContent(principal.getDeviceId(), sourceId, path, false, request, response);
    }

    @Operation(summary = "设备访问外部媒体缩略图")
    @GetMapping("/media-sources/{sourceId}/thumbnail")
    public void getDeviceExternalMediaThumbnail(@PathVariable Long sourceId,
                                                @RequestParam String path,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        DeviceAuthPrincipal principal = SecurityUtil.getCurrentDevicePrincipal();
        deviceService.writeDeviceExternalMediaContent(principal.getDeviceId(), sourceId, path, true, request, response);
    }

    @Operation(summary = "获取设备分组列表")
    @GetMapping("/groups")
    public Result<List<DeviceGroupResponse>> listGroups() {
        return Result.success(deviceService.listGroups(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "创建设备分组")
    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<DeviceGroupResponse> createGroup(@Valid @RequestBody DeviceGroupCreateRequest request) {
        return Result.success(deviceService.createGroup(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "更新设备分组")
    @PatchMapping("/groups/{id}")
    public Result<DeviceGroupResponse> updateGroup(@PathVariable Long id,
                                                   @RequestBody DeviceGroupUpdateRequest request) {
        return Result.success(deviceService.updateGroup(SecurityUtil.getCurrentUserId(), id, request));
    }

    @Operation(summary = "删除设备分组")
    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> deleteGroup(@PathVariable Long id) {
        deviceService.deleteGroup(SecurityUtil.getCurrentUserId(), id);
        return Result.success();
    }

    @Operation(summary = "添加设备到分组")
    @PostMapping("/groups/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Void> addGroupMember(@PathVariable Long id,
                                       @Valid @RequestBody DeviceGroupMemberRequest request) {
        deviceService.addGroupMember(SecurityUtil.getCurrentUserId(), id, request.getDeviceId());
        return Result.success();
    }

    @Operation(summary = "从分组移除设备")
    @DeleteMapping("/groups/{id}/members/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> removeGroupMember(@PathVariable Long id, @PathVariable Long deviceId) {
        deviceService.removeGroupMember(SecurityUtil.getCurrentUserId(), id, deviceId);
        return Result.success();
    }
}
