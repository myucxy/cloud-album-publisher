package com.cloudalbum.publisher.device.service;

import com.cloudalbum.publisher.device.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface DeviceService {

    List<DeviceResponse> listDevices(Long userId);

    List<DeviceResponse> listUnboundDevices();

    DeviceResponse registerUnboundDevice(DeviceSelfRegisterRequest request);

    DeviceResponse bindDevice(Long userId, DeviceBindRequest request);

    DeviceResponse bindUnboundDevice(Long userId, Long deviceId, AdminDeviceBindRequest request);

    void unbindDevice(Long userId, Long deviceId);

    DeviceResponse renameDevice(Long userId, Long deviceId, DeviceRenameRequest request);

    DeviceResponse updateDeviceStatus(Long userId, Long deviceId, DeviceStatusUpdateRequest request);

    DeviceTokenResponse createAccessToken(Long userId, DeviceTokenRequest request);

    DeviceTokenResponse createAccessTokenForCurrentDevice(DeviceTokenRequest request);

    DevicePullResponse pullContent(Long userId, String deviceUid);

    DevicePullResponse pullContentByDevice(Long deviceId);

    List<DeviceGroupResponse> listGroups(Long userId);

    DeviceGroupResponse createGroup(Long userId, DeviceGroupCreateRequest request);

    DeviceGroupResponse updateGroup(Long userId, Long groupId, DeviceGroupUpdateRequest request);

    void deleteGroup(Long userId, Long groupId);

    void addGroupMember(Long userId, Long groupId, Long deviceId);

    void removeGroupMember(Long userId, Long groupId, Long deviceId);

    int markOfflineDevices(long offlineThresholdSeconds);

    void writeDeviceMediaContent(Long deviceId, Long mediaId, boolean thumbnail, HttpServletRequest request, HttpServletResponse response);

    void writeDeviceAlbumCover(Long deviceId,
                               Long albumId,
                               HttpServletRequest request,
                               HttpServletResponse response);

    void writeDeviceAlbumBgm(Long deviceId,
                             Long albumId,
                             HttpServletRequest request,
                             HttpServletResponse response);

    void writeDeviceExternalMediaContent(Long deviceId,
                                         Long sourceId,
                                         String path,
                                         boolean thumbnail,
                                         HttpServletRequest request,
                                         HttpServletResponse response);
}
