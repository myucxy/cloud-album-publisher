package com.cloudalbum.publisher.device.service.impl;

import java.io.IOException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudalbum.publisher.album.entity.Album;
import com.cloudalbum.publisher.album.entity.AlbumBgm;
import com.cloudalbum.publisher.album.entity.AlbumMedia;
import com.cloudalbum.publisher.album.mapper.AlbumBgmMapper;
import com.cloudalbum.publisher.album.mapper.AlbumMapper;
import com.cloudalbum.publisher.album.support.AlbumDisplaySettingsSupport;
import com.cloudalbum.publisher.album.mapper.AlbumMediaMapper;
import com.cloudalbum.publisher.common.enums.DeviceStatus;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.util.JwtUtil;
import com.cloudalbum.publisher.device.dto.*;
import com.cloudalbum.publisher.device.entity.Device;
import com.cloudalbum.publisher.device.entity.DeviceGroup;
import com.cloudalbum.publisher.device.entity.DeviceGroupRel;
import com.cloudalbum.publisher.device.mapper.DeviceGroupMapper;
import com.cloudalbum.publisher.device.mapper.DeviceGroupRelMapper;
import com.cloudalbum.publisher.device.mapper.DeviceMapper;
import com.cloudalbum.publisher.device.service.DeviceService;
import com.cloudalbum.publisher.distribution.entity.Distribution;
import com.cloudalbum.publisher.distribution.entity.DistributionDevice;
import com.cloudalbum.publisher.distribution.mapper.DistributionDeviceMapper;
import com.cloudalbum.publisher.distribution.mapper.DistributionMapper;
import com.cloudalbum.publisher.media.content.MediaContentResolverRegistry;
import com.cloudalbum.publisher.media.content.MediaHttpWriter;
import com.cloudalbum.publisher.media.content.ObjectStorageMediaContentResolver;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseItemResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseResponse;
import com.cloudalbum.publisher.mediasource.service.MediaSourceService;
import com.cloudalbum.publisher.review.entity.ReviewRecord;
import com.cloudalbum.publisher.review.mapper.ReviewRecordMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${device.pull.chunk-size-distributions:2}")
    private int chunkSizeDistributions;

    private final DeviceMapper deviceMapper;
    private final DeviceGroupMapper deviceGroupMapper;
    private final DeviceGroupRelMapper deviceGroupRelMapper;
    private final DistributionMapper distributionMapper;
    private final DistributionDeviceMapper distributionDeviceMapper;
    private final AlbumMapper albumMapper;
    private final AlbumBgmMapper albumBgmMapper;
    private final AlbumMediaMapper albumMediaMapper;
    private final MediaMapper mediaMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final MediaContentResolverRegistry mediaContentResolverRegistry;
    private final MediaHttpWriter mediaHttpWriter;
    private final MediaSourceService mediaSourceService;
    private final JwtUtil jwtUtil;
    private final ObjectStorageMediaContentResolver objectStorageMediaContentResolver;
    private final RedisTemplate<String, Object> devicePullRedisTemplate;
    private final ObjectMapper devicePullObjectMapper;

    @Override
    public List<DeviceResponse> listDevices(Long userId) {
        List<Device> devices = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getUserId, userId)
                .ne(Device::getStatus, DeviceStatus.UNBOUND.name())
                .orderByDesc(Device::getUpdatedAt));
        Map<Long, DeviceGroupSummary> groupSummaryByDeviceId = loadGroupSummaryByDeviceId(devices);
        return devices.stream()
                .map(device -> toDeviceResponse(device, groupSummaryByDeviceId.get(device.getId())))
                .toList();
    }

    @Override
    public List<DeviceResponse> listUnboundDevices() {
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                        .eq(Device::getStatus, DeviceStatus.UNBOUND.name())
                        .orderByDesc(Device::getUpdatedAt))
                .stream().map(this::toDeviceResponse).toList();
    }

    @Override
    @Transactional
    public DeviceResponse registerUnboundDevice(DeviceSelfRegisterRequest request) {
        Device exists = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceUid, request.getDeviceUid())
                .last("limit 1"));

        if (exists != null
                && exists.getUserId() != null
                && !DeviceStatus.UNBOUND.name().equals(exists.getStatus())) {
            return toDeviceResponse(exists);
        }

        Device device = exists == null ? new Device() : exists;
        LocalDateTime now = LocalDateTime.now();
        device.setUserId(null);
        device.setDeviceUid(request.getDeviceUid());
        device.setType(request.getType());
        device.setName(StringUtils.hasText(request.getName()) ? request.getName() : request.getDeviceUid());
        device.setStatus(DeviceStatus.UNBOUND.name());
        device.setLastHeartbeatAt(now);

        if (exists == null) {
            deviceMapper.insert(device);
        } else {
            device.setUpdatedAt(now);
            deviceMapper.update(null, new LambdaUpdateWrapper<Device>()
                    .eq(Device::getId, device.getId())
                    .set(Device::getUserId, null)
                    .set(Device::getDeviceUid, device.getDeviceUid())
                    .set(Device::getType, device.getType())
                    .set(Device::getName, device.getName())
                    .set(Device::getStatus, device.getStatus())
                    .set(Device::getLastHeartbeatAt, device.getLastHeartbeatAt())
                    .set(Device::getUpdatedAt, device.getUpdatedAt()));
        }
        return toDeviceResponse(device);
    }

    @Override
    @Transactional
    public DeviceResponse bindDevice(Long userId, DeviceBindRequest request) {
        Device exists = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceUid, request.getDeviceUid())
                .last("limit 1"));

        if (exists != null && exists.getUserId() != null && !Objects.equals(exists.getUserId(), userId)) {
            throw new BusinessException(ResultCode.DEVICE_ALREADY_BOUND);
        }

        Device device = exists == null ? new Device() : exists;
        device.setUserId(userId);
        device.setDeviceUid(request.getDeviceUid());
        device.setType(request.getType());
        device.setName(StringUtils.hasText(request.getName()) ? request.getName() : request.getDeviceUid());
        device.setStatus(DeviceStatus.OFFLINE.name());
        device.setLastHeartbeatAt(LocalDateTime.now());
        device.setBoundAt(LocalDateTime.now());
        device.setUnboundAt(null);

        if (exists == null) {
            deviceMapper.insert(device);
        } else {
            deviceMapper.updateById(device);
        }
        return toDeviceResponse(device);
    }

    @Override
    @Transactional
    public DeviceResponse bindUnboundDevice(Long userId, Long deviceId, AdminDeviceBindRequest request) {
        Device device = getDevice(deviceId);
        boolean unbound = device.getUserId() == null || DeviceStatus.UNBOUND.name().equals(device.getStatus());
        if (!unbound && device.getUserId() != null && !Objects.equals(device.getUserId(), userId)) {
            throw new BusinessException(ResultCode.DEVICE_ALREADY_BOUND);
        }
        if (!unbound && device.getUserId() != null && Objects.equals(device.getUserId(), userId)) {
            device.setName(request.getName());
            deviceMapper.updateById(device);
            return toDeviceResponse(device);
        }

        device.setUserId(userId);
        device.setName(request.getName());
        device.setStatus(DeviceStatus.OFFLINE.name());
        device.setBoundAt(LocalDateTime.now());
        device.setUnboundAt(null);
        device.setLastHeartbeatAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        return toDeviceResponse(device);
    }

    @Override
    @Transactional
    public DeviceTokenResponse createAccessToken(Long userId, DeviceTokenRequest request) {
        Device device = getOwnedDeviceByUid(userId, request.getDeviceUid());
        ensureDeviceBound(device);
        String accessToken = jwtUtil.generateDeviceAccessToken(device.getUserId(), device.getId(), device.getDeviceUid());
        return new DeviceTokenResponse(
                accessToken,
                jwtUtil.getDeviceAccessTokenExpire(),
                device.getId(),
                device.getDeviceUid());
    }

    @Override
    @Transactional
    public DeviceTokenResponse createAccessTokenForCurrentDevice(DeviceTokenRequest request) {
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceUid, request.getDeviceUid())
                .last("limit 1"));
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        ensureDeviceBound(device);
        String accessToken = jwtUtil.generateDeviceAccessToken(device.getUserId(), device.getId(), device.getDeviceUid());
        return new DeviceTokenResponse(
                accessToken,
                jwtUtil.getDeviceAccessTokenExpire(),
                device.getId(),
                device.getDeviceUid());
    }

    @Override
    @Transactional
    public void unbindDevice(Long userId, Long deviceId) {
        Device device = getOwnedDevice(userId, deviceId);

        // 先删除关联数据
        deviceGroupRelMapper.delete(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getDeviceId, deviceId));
        distributionDeviceMapper.delete(new LambdaQueryWrapper<DistributionDevice>()
                .eq(DistributionDevice::getDeviceId, deviceId));

        // 物理删除设备记录
        deviceMapper.deleteById(deviceId);
    }

    @Override
    @Transactional
    public DeviceResponse renameDevice(Long userId, Long deviceId, DeviceRenameRequest request) {
        Device device = getOwnedDevice(userId, deviceId);
        device.setName(request.getName());
        deviceMapper.updateById(device);
        return toDeviceResponse(device);
    }

    @Override
    @Transactional
    public DeviceResponse updateDeviceStatus(Long userId, Long deviceId, DeviceStatusUpdateRequest request) {
        Device device = getOwnedDevice(userId, deviceId);
        DeviceStatus status = parseDeviceStatus(request.getStatus());
        if (status == DeviceStatus.UNBOUND) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设备状态不可直接设置为UNBOUND");
        }
        device.setStatus(status.name());
        if (status == DeviceStatus.ONLINE) {
            device.setLastHeartbeatAt(LocalDateTime.now());
        }
        deviceMapper.updateById(device);
        return toDeviceResponse(device);
    }

    @Override
    @Transactional
    public DevicePullResponse pullContent(Long userId, String deviceUid) {
        if (!StringUtils.hasText(deviceUid)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设备标识不能为空");
        }
        return buildPullResponse(getOwnedDeviceByUid(userId, deviceUid), false);
    }

    @Override
    @Transactional
    public DevicePullResponse pullContentByDevice(Long deviceId) {
        Device device = getDevice(deviceId);
        ensureDeviceBound(device);
        return buildPullResponse(device, true);
    }

    @Override
    public DevicePullChunkResponse pullContentByDeviceChunk(Long deviceId, String cursor) {
        Device device = getDevice(deviceId);
        ensureDeviceBound(device);
        return buildPullChunkResponse(device, true, cursor);
    }

    @Override
    public List<DeviceGroupResponse> listGroups(Long userId) {
        List<DeviceGroup> groups = deviceGroupMapper.selectList(new LambdaQueryWrapper<DeviceGroup>()
                .eq(DeviceGroup::getUserId, userId)
                .orderByDesc(DeviceGroup::getUpdatedAt));
        return groups.stream().map(this::toGroupResponse).toList();
    }

    @Override
    @Transactional
    public DeviceGroupResponse createGroup(Long userId, DeviceGroupCreateRequest request) {
        DeviceGroup group = new DeviceGroup();
        group.setUserId(userId);
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        deviceGroupMapper.insert(group);
        return toGroupResponse(group);
    }

    @Override
    @Transactional
    public DeviceGroupResponse updateGroup(Long userId, Long groupId, DeviceGroupUpdateRequest request) {
        DeviceGroup group = getOwnedGroup(userId, groupId);
        if (StringUtils.hasText(request.getName())) {
            group.setName(request.getName());
        }
        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }
        deviceGroupMapper.updateById(group);
        return toGroupResponse(group);
    }

    @Override
    @Transactional
    public void deleteGroup(Long userId, Long groupId) {
        DeviceGroup group = getOwnedGroup(userId, groupId);
        deviceGroupRelMapper.delete(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getGroupId, groupId));
        deviceGroupMapper.deleteById(group.getId());
    }

    @Override
    @Transactional
    public void addGroupMember(Long userId, Long groupId, Long deviceId) {
        getOwnedGroup(userId, groupId);
        Device device = getOwnedDevice(userId, deviceId);
        ensureDeviceBound(device);

        Long count = deviceGroupRelMapper.selectCount(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getGroupId, groupId)
                .eq(DeviceGroupRel::getDeviceId, deviceId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_MEMBER_EXISTS);
        }
        DeviceGroupRel rel = new DeviceGroupRel();
        rel.setGroupId(groupId);
        rel.setDeviceId(deviceId);
        deviceGroupRelMapper.insert(rel);
    }

    @Override
    @Transactional
    public void removeGroupMember(Long userId, Long groupId, Long deviceId) {
        getOwnedGroup(userId, groupId);
        Long count = deviceGroupRelMapper.selectCount(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getGroupId, groupId)
                .eq(DeviceGroupRel::getDeviceId, deviceId));
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_MEMBER_NOT_FOUND);
        }
        deviceGroupRelMapper.delete(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getGroupId, groupId)
                .eq(DeviceGroupRel::getDeviceId, deviceId));
    }

    @Override
    @Transactional
    public int markOfflineDevices(long offlineThresholdSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(offlineThresholdSeconds);
        List<Device> onlineDevices = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getStatus, DeviceStatus.ONLINE.name())
                .lt(Device::getLastHeartbeatAt, threshold));
        for (Device device : onlineDevices) {
            device.setStatus(DeviceStatus.OFFLINE.name());
            deviceMapper.updateById(device);
        }
        return onlineDevices.size();
    }

    @Override
    public void writeDeviceMediaContent(Long deviceId, Long mediaId, boolean thumbnail, HttpServletRequest request, HttpServletResponse response) {
        Device device = getDevice(deviceId);
        ensureDeviceBound(device);
        Media media = mediaMapper.selectById(mediaId);
        if (media == null) {
            sendHttpError(response, HttpServletResponse.SC_NOT_FOUND, "媒体不存在");
            return;
        }
        if (!Objects.equals(media.getUserId(), device.getUserId())) {
            sendHttpError(response, HttpServletResponse.SC_FORBIDDEN, "无权访问该媒体");
            return;
        }
        if (!"READY".equals(media.getStatus())) {
            sendHttpError(response, HttpServletResponse.SC_CONFLICT, "媒体尚未就绪");
            return;
        }
        ReviewRecord latestReview = queryLatestReviewMap(List.of(mediaId)).get(mediaId);
        if (latestReview == null || !"APPROVED".equals(latestReview.getStatus())) {
            sendHttpError(response, HttpServletResponse.SC_FORBIDDEN, "媒体内容尚未通过审核");
            return;
        }
        mediaHttpWriter.write(
                mediaContentResolverRegistry.resolve(media, thumbnail),
                request,
                response,
                "读取媒体内容失败");
    }

    @Override
    public void writeDeviceAlbumCover(Long deviceId,
                                      Long albumId,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        Device device = getDevice(deviceId);
        ensureDeviceBound(device);
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            sendHttpError(response, HttpServletResponse.SC_NOT_FOUND, "相册不存在");
            return;
        }
        if (!Objects.equals(album.getUserId(), device.getUserId())) {
            sendHttpError(response, HttpServletResponse.SC_FORBIDDEN, "无权访问该相册");
            return;
        }
        if (!canDeviceAccessAlbum(album, true)) {
            sendHttpError(response, HttpServletResponse.SC_FORBIDDEN, "无权访问该相册");
            return;
        }
        if (hasExternalCover(album)) {
            try {
                mediaSourceService.writeMediaContent(
                        album.getCoverSourceId(),
                        album.getUserId(),
                        album.getCoverPath(),
                        shouldUseExternalThumbnailForCover(album),
                        request,
                        response);
            } catch (BusinessException e) {
                sendHttpError(response, e.getCode() == 404 ? HttpServletResponse.SC_NOT_FOUND : HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            }
            return;
        }

        Media coverMedia = resolveCoverMedia(album);
        if (coverMedia != null) {
            mediaHttpWriter.write(
                    mediaContentResolverRegistry.resolve(coverMedia, shouldUseThumbnailForCover(coverMedia)),
                    request,
                    response,
                    "读取相册封面失败");
            return;
        }

        String objectKey = resolveCoverObjectKey(album.getCoverUrl());
        if (!StringUtils.hasText(objectKey)) {
            sendHttpError(response, HttpServletResponse.SC_NOT_FOUND, "相册封面不存在");
            return;
        }

        mediaHttpWriter.write(
                objectStorageMediaContentResolver.resolveObject(bucket, objectKey, "application/octet-stream", "相册封面不存在"),
                request,
                response,
                "读取相册封面失败");
    }

    @Override
    public void writeDeviceAlbumBgm(Long deviceId,
                                    Long albumId,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        Device device = getDevice(deviceId);
        ensureDeviceBound(device);
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            sendHttpError(response, HttpServletResponse.SC_NOT_FOUND, "相册不存在");
            return;
        }
        if (!Objects.equals(album.getUserId(), device.getUserId())) {
            sendHttpError(response, HttpServletResponse.SC_FORBIDDEN, "无权访问该相册");
            return;
        }
        if (!canDeviceAccessAlbum(album, true)) {
            sendHttpError(response, HttpServletResponse.SC_FORBIDDEN, "无权访问该相册");
            return;
        }
        if (hasExternalBgm(album)) {
            try {
                mediaSourceService.writeMediaContent(
                        album.getBgmSourceId(),
                        album.getUserId(),
                        album.getBgmPath(),
                        false,
                        request,
                        response);
            } catch (BusinessException e) {
                sendHttpError(response, e.getCode() == 404 ? HttpServletResponse.SC_NOT_FOUND : HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            }
            return;
        }

        Media bgmMedia = resolveBgmMedia(album);
        if (bgmMedia != null) {
            mediaHttpWriter.write(
                    mediaContentResolverRegistry.resolve(bgmMedia, false),
                    request,
                    response,
                    "读取相册BGM失败");
            return;
        }

        sendHttpError(response, HttpServletResponse.SC_NOT_FOUND, "相册BGM不存在");
    }

    @Override
    public void writeDeviceExternalMediaContent(Long deviceId,
                                                Long sourceId,
                                                String path,
                                                boolean thumbnail,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        Device device = getDevice(deviceId);
        ensureDeviceBound(device);
        try {
            resolveExternalMediaItem(sourceId, device.getUserId(), path, null);
            mediaSourceService.writeMediaContent(sourceId, device.getUserId(), path, thumbnail, request, response);
        } catch (BusinessException e) {
            sendHttpError(response, e.getCode() == 404 ? HttpServletResponse.SC_NOT_FOUND : HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        }
    }

    private DevicePullResponse buildPullResponse(Device device, boolean deviceTokenAccess) {
        ensureDeviceBound(device);
        LocalDateTime now = LocalDateTime.now();
        device.setStatus(DeviceStatus.ONLINE.name());
        device.setLastHeartbeatAt(now);
        deviceMapper.updateById(device);

        DevicePullResponse response = new DevicePullResponse();
        response.setDevice(toDeviceResponse(device));
        response.setPulledAt(now);

        List<Long> distributionIds = queryTargetDistributionIds(device.getId());
        if (CollectionUtils.isEmpty(distributionIds)) {
            response.setDistributions(Collections.emptyList());
            return response;
        }

        List<Distribution> distributions = distributionMapper.selectList(
                new LambdaQueryWrapper<Distribution>()
                        .in(Distribution::getId, distributionIds)
                        .eq(Distribution::getUserId, device.getUserId())
                        .eq(Distribution::getStatus, "ACTIVE")
                        .orderByDesc(Distribution::getCreatedAt));

        Map<String, MediaSourceBrowseResponse> externalBrowseCache = new HashMap<>();
        Map<Long, Album> albumCache = loadAlbumMap(distributions);
        Map<Long, List<AlbumMedia>> albumMediaCache = loadAlbumMediaMap(albumCache.keySet());
        Set<Long> allInternalMediaIds = albumMediaCache.values().stream()
                .flatMap(List::stream)
                .map(AlbumMedia::getMediaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Media> mediaCache = loadMediaMap(allInternalMediaIds);
        Map<Long, ReviewRecord> reviewCache = queryLatestReviewMap(new ArrayList<>(allInternalMediaIds));
        Map<Long, List<AlbumBgm>> bgmCache = loadAlbumBgmMap(albumCache.keySet());

        List<DevicePullResponse.DistributionItem> items = distributions.stream()
                .map(distribution -> toPullDistribution(distribution, device.getUserId(), deviceTokenAccess, true, externalBrowseCache, albumCache, albumMediaCache, mediaCache, reviewCache, bgmCache))
                .filter(Objects::nonNull)
                .toList();
        response.setDistributions(items);
        return response;
    }

    private DevicePullChunkResponse buildPullChunkResponse(Device device, boolean deviceTokenAccess, String cursor) {
        ensureDeviceBound(device);
        LocalDateTime now = LocalDateTime.now();
        device.setStatus(DeviceStatus.ONLINE.name());
        device.setLastHeartbeatAt(now);
        deviceMapper.updateById(device);

        DevicePullSnapshot snapshot;
        int nextDistributionIndex;
        int returnedMediaCount;

        if (!StringUtils.hasText(cursor)) {
            List<Long> distributionIds = queryTargetDistributionIds(device.getId());
            List<Long> orderedDistributionIds = CollectionUtils.isEmpty(distributionIds) ? Collections.emptyList() : distributionMapper.selectList(
                    new LambdaQueryWrapper<Distribution>()
                            .in(Distribution::getId, distributionIds)
                            .eq(Distribution::getUserId, device.getUserId())
                            .eq(Distribution::getStatus, "ACTIVE")
                            .orderByDesc(Distribution::getCreatedAt)
            ).stream().map(Distribution::getId).toList();

            String snapshotId = UUID.randomUUID().toString();
            snapshot = new DevicePullSnapshot();
            snapshot.setSnapshotId(snapshotId);
            snapshot.setUserId(device.getUserId());
            snapshot.setDeviceTokenAccess(deviceTokenAccess);
            snapshot.setCreatedAt(now);
            snapshot.setOrderedDistributionIds(orderedDistributionIds);
            nextDistributionIndex = 0;
            returnedMediaCount = 0;
            storeDevicePullSnapshot(snapshot);
        } else {
            PullChunkCursor parsedCursor = parsePullChunkCursor(cursor);
            snapshot = loadDevicePullSnapshot(parsedCursor.snapshotId());
            if (snapshot == null || !Objects.equals(snapshot.getUserId(), device.getUserId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "拉取快照已失效，请重新开始拉取");
            }
            deviceTokenAccess = snapshot.isDeviceTokenAccess();
            nextDistributionIndex = parsedCursor.nextDistributionIndex();
            returnedMediaCount = parsedCursor.returnedMediaCount();
        }

        DevicePullChunkResponse response = new DevicePullChunkResponse();
        response.setDevice(toDeviceResponse(device));
        response.setPulledAt(now);
        response.setSnapshotId(snapshot.getSnapshotId());

        List<Long> orderedDistributionIds = snapshot.getOrderedDistributionIds();
        if (CollectionUtils.isEmpty(orderedDistributionIds) || nextDistributionIndex >= orderedDistributionIds.size()) {
            response.setDistributions(Collections.emptyList());
            response.setHasMore(false);
            response.setFinalChunk(true);
            response.setCursor(null);
            response.setReturnedDistributionCount(0);
            response.setReturnedMediaCount(0);
            refreshDevicePullSnapshotTtl(snapshot.getSnapshotId());
            return response;
        }

        List<Long> chunkDistributionIds = orderedDistributionIds.subList(nextDistributionIndex, Math.min(nextDistributionIndex + chunkSizeDistributions, orderedDistributionIds.size()));
        Map<Long, Distribution> distributionById = distributionMapper.selectList(
                new LambdaQueryWrapper<Distribution>()
                        .in(Distribution::getId, chunkDistributionIds)
                        .eq(Distribution::getUserId, device.getUserId())
                        .eq(Distribution::getStatus, "ACTIVE")
        ).stream().collect(Collectors.toMap(Distribution::getId, Function.identity(), (a, b) -> a));

        Map<String, MediaSourceBrowseResponse> externalBrowseCache = new HashMap<>();
        List<Distribution> chunkDistributions = chunkDistributionIds.stream()
                .map(distributionById::get)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Album> albumCache = loadAlbumMap(chunkDistributions);
        Map<Long, List<AlbumMedia>> albumMediaCache = loadAlbumMediaMap(albumCache.keySet());
        Set<Long> allInternalMediaIds = albumMediaCache.values().stream()
                .flatMap(List::stream)
                .map(AlbumMedia::getMediaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Media> mediaCache = loadMediaMap(allInternalMediaIds);
        Map<Long, ReviewRecord> reviewCache = queryLatestReviewMap(new ArrayList<>(allInternalMediaIds));
        Map<Long, List<AlbumBgm>> bgmCache = loadAlbumBgmMap(albumCache.keySet());

        List<DevicePullResponse.DistributionItem> items = new ArrayList<>();
        int chunkReturnedMediaCount = 0;
        int nextIndexAfterChunk = nextDistributionIndex;

        for (Long distributionId : chunkDistributionIds) {
            Distribution distribution = distributionById.get(distributionId);
            nextIndexAfterChunk++;
            if (distribution == null) {
                continue;
            }
            DevicePullResponse.DistributionItem item = toPullDistribution(distribution, device.getUserId(), deviceTokenAccess, true, externalBrowseCache, albumCache, albumMediaCache, mediaCache, reviewCache, bgmCache);
            if (item == null) {
                continue;
            }
            int mediaCount = item.getMediaList() == null ? 0 : item.getMediaList().size();
            items.add(item);
            chunkReturnedMediaCount += mediaCount;
        }

        response.setDistributions(items);
        response.setReturnedDistributionCount(items.size());
        response.setReturnedMediaCount(chunkReturnedMediaCount);

        boolean hasMore = nextIndexAfterChunk < orderedDistributionIds.size();
        response.setHasMore(hasMore);
        response.setFinalChunk(!hasMore);

        if (hasMore) {
            PullChunkCursor nextCursor = new PullChunkCursor(snapshot.getSnapshotId(), nextIndexAfterChunk, returnedMediaCount + chunkReturnedMediaCount);
            response.setCursor(encodePullChunkCursor(nextCursor));
        } else {
            response.setCursor(null);
            deleteDevicePullSnapshot(snapshot.getSnapshotId());
        }

        if (hasMore) {
            refreshDevicePullSnapshotTtl(snapshot.getSnapshotId());
        }

        return response;
    }

    private Map<Long, Album> loadAlbumMap(List<Distribution> distributions) {
        Set<Long> albumIds = distributions.stream().map(Distribution::getAlbumId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (albumIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return albumMapper.selectBatchIds(albumIds).stream().collect(Collectors.toMap(Album::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, List<AlbumMedia>> loadAlbumMediaMap(Set<Long> albumIds) {
        if (CollectionUtils.isEmpty(albumIds)) {
            return Collections.emptyMap();
        }
        List<AlbumMedia> list = albumMediaMapper.selectList(new LambdaQueryWrapper<AlbumMedia>()
                .in(AlbumMedia::getAlbumId, albumIds)
                .orderByAsc(AlbumMedia::getSortOrder)
                .orderByAsc(AlbumMedia::getId));
        Map<Long, List<AlbumMedia>> map = new HashMap<>();
        for (AlbumMedia albumMedia : list) {
            map.computeIfAbsent(albumMedia.getAlbumId(), key -> new ArrayList<>()).add(albumMedia);
        }
        return map;
    }

    private Map<Long, Media> loadMediaMap(Set<Long> mediaIds) {
        if (CollectionUtils.isEmpty(mediaIds)) {
            return Collections.emptyMap();
        }
        return mediaMapper.selectBatchIds(mediaIds).stream().collect(Collectors.toMap(Media::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, List<AlbumBgm>> loadAlbumBgmMap(Set<Long> albumIds) {
        if (CollectionUtils.isEmpty(albumIds)) {
            return Collections.emptyMap();
        }
        List<AlbumBgm> list = albumBgmMapper.selectList(new LambdaQueryWrapper<AlbumBgm>()
                .in(AlbumBgm::getAlbumId, albumIds)
                .orderByAsc(AlbumBgm::getSortOrder)
                .orderByAsc(AlbumBgm::getId));
        Map<Long, List<AlbumBgm>> map = new HashMap<>();
        for (AlbumBgm bgm : list) {
            map.computeIfAbsent(bgm.getAlbumId(), key -> new ArrayList<>()).add(bgm);
        }
        return map;
    }

    private List<Long> queryTargetDistributionIds(Long deviceId) {
        LinkedHashSet<Long> distributionIds = new LinkedHashSet<>();

        List<DistributionDevice> directRelations = distributionDeviceMapper.selectList(
                new LambdaQueryWrapper<DistributionDevice>()
                        .eq(DistributionDevice::getDeviceId, deviceId));
        directRelations.stream()
                .map(DistributionDevice::getDistributionId)
                .forEach(distributionIds::add);

        List<DeviceGroupRel> groupRelations = deviceGroupRelMapper.selectList(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getDeviceId, deviceId));
        if (!CollectionUtils.isEmpty(groupRelations)) {
            List<Long> groupIds = groupRelations.stream().map(DeviceGroupRel::getGroupId).distinct().toList();
            List<DistributionDevice> groupTargets = distributionDeviceMapper.selectList(
                    new LambdaQueryWrapper<DistributionDevice>()
                            .in(DistributionDevice::getGroupId, groupIds));
            groupTargets.stream()
                    .map(DistributionDevice::getDistributionId)
                    .forEach(distributionIds::add);
        }
        return distributionIds.stream().toList();
    }

    private DevicePullResponse.DistributionItem toPullDistribution(Distribution distribution,
                                                                   Long userId,
                                                                   boolean deviceTokenAccess,
                                                                   boolean trustExternalMetadata,
                                                                   Map<String, MediaSourceBrowseResponse> externalBrowseCache,
                                                                   Map<Long, Album> albumCache,
                                                                   Map<Long, List<AlbumMedia>> albumMediaCache,
                                                                   Map<Long, Media> mediaCache,
                                                                   Map<Long, ReviewRecord> reviewCache,
                                                                   Map<Long, List<AlbumBgm>> bgmCache) {
        Album album = albumCache.get(distribution.getAlbumId());
        if (album == null || !Objects.equals(album.getUserId(), userId)) {
            return null;
        }
        if (!canDeviceAccessAlbum(album, deviceTokenAccess)) {
            return null;
        }

        List<AlbumMedia> albumMediaList = albumMediaCache.getOrDefault(album.getId(), Collections.emptyList());
        if (CollectionUtils.isEmpty(albumMediaList)) {
            return null;
        }

        List<DevicePullResponse.MediaItem> mediaItems = albumMediaList.stream()
                .map(albumMedia -> toPullMediaItem(albumMedia,
                        albumMedia.getMediaId() == null ? null : mediaCache.get(albumMedia.getMediaId()),
                        albumMedia.getMediaId() == null ? null : reviewCache.get(albumMedia.getMediaId()),
                        userId,
                        distribution.getItemDuration(),
                        trustExternalMetadata,
                        externalBrowseCache))
                .filter(Objects::nonNull)
                .toList();
        if (CollectionUtils.isEmpty(mediaItems)) {
            return null;
        }

        DevicePullResponse.DistributionItem item = new DevicePullResponse.DistributionItem();
        item.setId(distribution.getId());
        item.setName(distribution.getName());
        item.setStatus(distribution.getStatus());
        item.setLoopPlay(distribution.getLoopPlay());
        item.setShuffle(distribution.getShuffle());
        item.setItemDuration(distribution.getItemDuration());
        item.setAlbum(toPullAlbumItem(album, distribution, trustExternalMetadata, externalBrowseCache, bgmCache.getOrDefault(album.getId(), Collections.emptyList())));
        item.setMediaList(mediaItems);
        return item;
    }

    private boolean canDeviceAccessAlbum(Album album, boolean deviceTokenAccess) {
        String visibility = album.getVisibility();
        if (!StringUtils.hasText(visibility)) {
            return true;
        }
        if ("PRIVATE".equals(visibility)) {
            return false;
        }
        return deviceTokenAccess || !"DEVICE_ONLY".equals(visibility);
    }

    private DevicePullResponse.AlbumItem toPullAlbumItem(Album album,
                                                        Distribution distribution,
                                                        boolean trustExternalMetadata,
                                                        Map<String, MediaSourceBrowseResponse> externalBrowseCache,
                                                        List<AlbumBgm> cachedBgms) {
        DevicePullResponse.AlbumItem item = new DevicePullResponse.AlbumItem();
        item.setId(album.getId());
        item.setTitle(album.getTitle());
        item.setDescription(album.getDescription());
        item.setCoverUrl(buildAlbumCoverUrl(album));
        List<DevicePullResponse.BgmItem> bgmList = cachedBgms.stream()
                .map(bgm -> toPullBgmItem(bgm, album.getUserId(), trustExternalMetadata, externalBrowseCache))
                .filter(Objects::nonNull)
                .toList();
        item.setBgmList(bgmList);
        item.setBgmUrl(!bgmList.isEmpty() ? bgmList.get(0).getUrl() : buildAlbumBgmUrl(album, trustExternalMetadata, externalBrowseCache));
        item.setBgmVolume(album.getBgmVolume());
        item.setTransitionStyle(AlbumDisplaySettingsSupport.resolveTransitionStyle(
                StringUtils.hasText(distribution.getTransitionStyle()) ? distribution.getTransitionStyle() : album.getTransitionStyle()));
        item.setDisplayStyle(AlbumDisplaySettingsSupport.resolveDisplayStyle(
                StringUtils.hasText(distribution.getDisplayStyle()) ? distribution.getDisplayStyle() : album.getDisplayStyle()));
        item.setDisplayVariant(AlbumDisplaySettingsSupport.resolveDisplayVariant(
                StringUtils.hasText(distribution.getDisplayVariant()) ? distribution.getDisplayVariant() : album.getDisplayVariant()));
        item.setShowTimeAndDate(distribution.getShowTimeAndDate() != null
                ? distribution.getShowTimeAndDate()
                : Boolean.TRUE.equals(album.getShowTimeAndDate()));
        item.setVisibility(album.getVisibility());
        return item;
    }

    private DevicePullResponse.BgmItem toPullBgmItem(AlbumBgm bgm, Long userId, boolean trustExternalMetadata, Map<String, MediaSourceBrowseResponse> externalBrowseCache) {
        if (bgm.isExternal()) {
            if (trustExternalMetadata && bgm.getSourceId() != null && StringUtils.hasText(bgm.getFilePath())) {
                DevicePullResponse.BgmItem item = new DevicePullResponse.BgmItem();
                item.setId(bgm.getId());
                item.setExternalMediaKey(bgm.getExternalMediaKey());
                item.setSourceId(bgm.getSourceId());
                item.setSourceType(bgm.getSourceType());
                item.setFileName(bgm.getFileName());
                item.setMediaType(bgm.getMediaType());
                item.setContentType(bgm.getContentType());
                item.setUrl(buildDeviceExternalContentUrl(bgm.getSourceId(), bgm.getFilePath()));
                item.setSortOrder(bgm.getSortOrder());
                return item;
            }
            MediaSourceBrowseItemResponse externalItem = resolveExternalMediaItem(
                    bgm.getSourceId(),
                    userId,
                    bgm.getFilePath(),
                    bgm.getExternalMediaKey(),
                    externalBrowseCache);
            DevicePullResponse.BgmItem item = new DevicePullResponse.BgmItem();
            item.setId(bgm.getId());
            item.setExternalMediaKey(externalItem.getExternalMediaKey());
            item.setSourceId(externalItem.getSourceId());
            item.setSourceType(externalItem.getSourceType());
            item.setFileName(firstText(externalItem.getFileName(), externalItem.getName(), bgm.getFileName()));
            item.setMediaType(firstText(externalItem.getMediaType(), bgm.getMediaType(), bgm.getMediaType()));
            item.setContentType(firstText(externalItem.getContentType(), bgm.getContentType(), bgm.getContentType()));
            item.setUrl(buildDeviceExternalContentUrl(externalItem.getSourceId(), externalItem.getPath()));
            item.setSortOrder(bgm.getSortOrder());
            return item;
        }

        Media media = mediaMapper.selectById(bgm.getMediaId());
        if (media == null || !Objects.equals(media.getUserId(), userId) || !"READY".equals(media.getStatus()) || !"AUDIO".equals(media.getMediaType())) {
            return null;
        }
        DevicePullResponse.BgmItem item = new DevicePullResponse.BgmItem();
        item.setId(bgm.getId());
        item.setMediaId(media.getId());
        item.setSourceId(firstNonNull(media.getSourceId(), bgm.getSourceId()));
        item.setSourceType(media.getSourceType());
        item.setFileName(media.getFileName());
        item.setMediaType(media.getMediaType());
        item.setContentType(media.getContentType());
        item.setUrl(buildDeviceContentUrl(media.getId()));
        item.setSortOrder(bgm.getSortOrder());
        return item;
    }

    private List<AlbumBgm> loadAlbumBgms(Long albumId) {
        return albumBgmMapper.selectList(new LambdaQueryWrapper<AlbumBgm>()
                .eq(AlbumBgm::getAlbumId, albumId)
                .orderByAsc(AlbumBgm::getSortOrder)
                .orderByAsc(AlbumBgm::getId));
    }

    private String buildAlbumBgmUrl(Album album, boolean trustExternalMetadata, Map<String, MediaSourceBrowseResponse> externalBrowseCache) {
        List<AlbumBgm> bgms = loadAlbumBgms(album.getId());
        if (!bgms.isEmpty()) {
            DevicePullResponse.BgmItem first = toPullBgmItem(bgms.get(0), album.getUserId(), trustExternalMetadata, externalBrowseCache);
            return first != null ? first.getUrl() : null;
        }
        if (hasExternalBgm(album) || resolveBgmMedia(album) != null) {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/devices/albums/{id}/bgm")
                    .buildAndExpand(album.getId())
                    .toUriString();
        }
        return album.getBgmUrl();
    }

    private boolean hasExternalBgm(Album album) {
        return album.getBgmSourceId() != null
                && StringUtils.hasText(album.getBgmExternalMediaKey())
                && StringUtils.hasText(album.getBgmPath());
    }

    private Media resolveBgmMedia(Album album) {
        if (album.getBgmMediaId() == null) {
            return null;
        }
        Media media = mediaMapper.selectById(album.getBgmMediaId());
        if (media == null || !Objects.equals(media.getUserId(), album.getUserId())) {
            return null;
        }
        return media;
    }

    private DevicePullResponse.MediaItem toPullMediaItem(AlbumMedia albumMedia,
                                                         Media media,
                                                         ReviewRecord latestReview,
                                                         Long userId,
                                                         Integer distributionItemDuration,
                                                         boolean trustExternalMetadata,
                                                         Map<String, MediaSourceBrowseResponse> externalBrowseCache) {
        if (albumMedia.isExternal()) {
            return toPullExternalMediaItem(albumMedia, userId, distributionItemDuration, trustExternalMetadata, externalBrowseCache);
        }
        return toPullInternalMediaItem(albumMedia, media, latestReview, userId, distributionItemDuration);
    }

    private DevicePullResponse.MediaItem toPullInternalMediaItem(AlbumMedia albumMedia,
                                                                 Media media,
                                                                 ReviewRecord latestReview,
                                                                 Long userId,
                                                                 Integer distributionItemDuration) {
        if (media == null || !Objects.equals(media.getUserId(), userId)) {
            return null;
        }
        if (!"READY".equals(media.getStatus())) {
            return null;
        }
        if (latestReview == null || !"APPROVED".equals(latestReview.getStatus())) {
            return null;
        }

        DevicePullResponse.MediaItem item = new DevicePullResponse.MediaItem();
        item.setId(media.getId());
        item.setSourceId(firstNonNull(media.getSourceId(), albumMedia.getSourceId()));
        item.setSourceType(media.getSourceType());
        item.setFileName(media.getFileName());
        item.setMediaType(media.getMediaType());
        item.setContentType(media.getContentType());
        item.setUrl(buildDeviceContentUrl(media.getId()));
        item.setThumbnailUrl(buildDeviceThumbnailUrl(media.getId(), media.getThumbnailKey()));
        item.setDurationSec(media.getDurationSec());
        item.setWidth(media.getWidth());
        item.setHeight(media.getHeight());
        item.setSortOrder(albumMedia.getSortOrder());
        item.setItemDuration(resolveItemDuration(albumMedia, distributionItemDuration));
        item.setFocalPointX(albumMedia.getFocalPointX());
        item.setFocalPointY(albumMedia.getFocalPointY());
        item.setFocalPointRegionType(albumMedia.getFocalPointRegionType());
        item.setFocalPointRegionWidth(albumMedia.getFocalPointRegionWidth());
        item.setFocalPointRegionHeight(albumMedia.getFocalPointRegionHeight());
        return item;
    }

    private DevicePullResponse.MediaItem toPullExternalMediaItem(AlbumMedia albumMedia,
                                                                 Long userId,
                                                                 Integer distributionItemDuration,
                                                                 boolean trustExternalMetadata,
                                                                 Map<String, MediaSourceBrowseResponse> externalBrowseCache) {
        if (trustExternalMetadata && albumMedia.getSourceId() != null && StringUtils.hasText(albumMedia.getFilePath())) {
            DevicePullResponse.MediaItem item = new DevicePullResponse.MediaItem();
            item.setExternalMediaKey(albumMedia.getExternalMediaKey());
            item.setSourceId(albumMedia.getSourceId());
            item.setSourceType(albumMedia.getSourceType());
            item.setFileName(albumMedia.getFileName());
            item.setMediaType(albumMedia.getMediaType());
            item.setContentType(albumMedia.getContentType());
            item.setUrl(buildDeviceExternalContentUrl(albumMedia.getSourceId(), albumMedia.getFilePath()));
            item.setThumbnailUrl(buildDeviceExternalThumbnailUrl(albumMedia.getSourceId(), albumMedia.getFilePath(), albumMedia.getMediaType()));
            item.setSortOrder(albumMedia.getSortOrder());
            item.setItemDuration(resolveItemDuration(albumMedia, distributionItemDuration));
            item.setFocalPointX(albumMedia.getFocalPointX());
            item.setFocalPointY(albumMedia.getFocalPointY());
            item.setFocalPointRegionType(albumMedia.getFocalPointRegionType());
            item.setFocalPointRegionWidth(albumMedia.getFocalPointRegionWidth());
            item.setFocalPointRegionHeight(albumMedia.getFocalPointRegionHeight());
            return item;
        }

        MediaSourceBrowseItemResponse externalItem = resolveExternalMediaItem(
                albumMedia.getSourceId(),
                userId,
                albumMedia.getFilePath(),
                albumMedia.getExternalMediaKey(),
                externalBrowseCache);
        DevicePullResponse.MediaItem item = new DevicePullResponse.MediaItem();
        item.setExternalMediaKey(externalItem.getExternalMediaKey());
        item.setSourceId(externalItem.getSourceId());
        item.setSourceType(externalItem.getSourceType());
        item.setFileName(firstText(externalItem.getFileName(), externalItem.getName(), albumMedia.getFileName()));
        item.setMediaType(firstText(externalItem.getMediaType(), albumMedia.getMediaType(), albumMedia.getMediaType()));
        item.setContentType(firstText(externalItem.getContentType(), albumMedia.getContentType(), albumMedia.getContentType()));
        item.setUrl(buildDeviceExternalContentUrl(externalItem.getSourceId(), externalItem.getPath()));
        item.setThumbnailUrl(buildDeviceExternalThumbnailUrl(externalItem.getSourceId(), externalItem.getPath(), externalItem.getMediaType()));
        item.setSortOrder(albumMedia.getSortOrder());
        item.setItemDuration(resolveItemDuration(albumMedia, distributionItemDuration));
        item.setFocalPointX(albumMedia.getFocalPointX());
        item.setFocalPointY(albumMedia.getFocalPointY());
        item.setFocalPointRegionType(albumMedia.getFocalPointRegionType());
        item.setFocalPointRegionWidth(albumMedia.getFocalPointRegionWidth());
        item.setFocalPointRegionHeight(albumMedia.getFocalPointRegionHeight());
        return item;
    }

    private Integer resolveItemDuration(AlbumMedia albumMedia, Integer distributionItemDuration) {
        Integer duration = albumMedia.getDuration();
        if (duration != null && duration > 0) {
            return duration;
        }
        return distributionItemDuration;
    }

    private String buildAlbumCoverUrl(Album album) {
        if (StringUtils.hasText(album.getCoverUrl()) || hasExternalCover(album) || resolveCoverMedia(album) != null) {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/devices/albums/{id}/cover")
                    .buildAndExpand(album.getId())
                    .toUriString();
        }
        return null;
    }

    private boolean hasExternalCover(Album album) {
        return album.getCoverSourceId() != null
                && StringUtils.hasText(album.getCoverExternalMediaKey())
                && StringUtils.hasText(album.getCoverPath());
    }

    private boolean shouldUseExternalThumbnailForCover(Album album) {
        String mediaType = album.getCoverMediaType();
        return !StringUtils.hasText(mediaType) || !"IMAGE".equals(mediaType);
    }

    private Media resolveCoverMedia(Album album) {
        if (album.getCoverMediaId() == null) {
            return null;
        }
        Media media = mediaMapper.selectById(album.getCoverMediaId());
        if (media == null || !Objects.equals(media.getUserId(), album.getUserId())) {
            return null;
        }
        return media;
    }

    private boolean shouldUseThumbnailForCover(Media media) {
        return !"IMAGE".equals(media.getMediaType());
    }

    private String resolveCoverObjectKey(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(coverUrl);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            String prefix = "/" + bucket + "/";
            if (path.startsWith(prefix)) {
                return path.substring(prefix.length());
            }
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private MediaSourceBrowseItemResponse resolveExternalMediaItem(Long sourceId,
                                                                   Long userId,
                                                                   String path,
                                                                   String externalMediaKey) {
        return resolveExternalMediaItem(sourceId, userId, path, externalMediaKey, new HashMap<>());
    }

    private MediaSourceBrowseItemResponse resolveExternalMediaItem(Long sourceId,
                                                                   Long userId,
                                                                   String path,
                                                                   String externalMediaKey,
                                                                   Map<String, MediaSourceBrowseResponse> externalBrowseCache) {
        if (sourceId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "媒体源不能为空");
        }
        String normalizedPath = normalizeExternalPath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "媒体路径不能为空");
        }
        String parentPath = parentPath(normalizedPath);
        String cacheKey = sourceId + "|" + parentPath;
        MediaSourceBrowseResponse browse = externalBrowseCache.computeIfAbsent(cacheKey, key -> mediaSourceService.browse(sourceId, userId, parentPath));
        if (browse == null || CollectionUtils.isEmpty(browse.getItems())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "外部媒体不存在");
        }
        if (StringUtils.hasText(externalMediaKey)) {
            return browse.getItems().stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getDirectory()))
                    .filter(item -> externalMediaKey.equals(item.getExternalMediaKey()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "外部媒体不存在"));
        }
        return browse.getItems().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getDirectory()))
                .filter(item -> Objects.equals(normalizeExternalPath(item.getPath()), normalizedPath))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "外部媒体不存在"));
    }

    private String normalizeExternalPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String parentPath(String path) {
        String normalized = normalizeExternalPath(path);
        if (!StringUtils.hasText(normalized) || "/".equals(normalized)) {
            return "/";
        }
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex <= 0) {
            return "/";
        }
        return normalized.substring(0, slashIndex);
    }

    private Map<Long, ReviewRecord> queryLatestReviewMap(List<Long> mediaIds) {
        if (CollectionUtils.isEmpty(mediaIds)) {
            return Collections.emptyMap();
        }
        List<ReviewRecord> list = reviewRecordMapper.selectList(new LambdaQueryWrapper<ReviewRecord>()
                .in(ReviewRecord::getMediaId, mediaIds)
                .orderByDesc(ReviewRecord::getCreatedAt));
        Map<Long, ReviewRecord> reviewMap = new HashMap<>();
        for (ReviewRecord record : list) {
            reviewMap.putIfAbsent(record.getMediaId(), record);
        }
        return reviewMap;
    }

    private void sendHttpError(HttpServletResponse response, int status, String message) {
        try {
            response.sendError(status, message);
        } catch (IOException ignored) {
        }
    }

    private String buildDeviceContentUrl(Long mediaId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/devices/media/{id}/content")
                .buildAndExpand(mediaId)
                .toUriString();
    }

    private String buildDeviceThumbnailUrl(Long mediaId, String thumbnailKey) {
        if (!StringUtils.hasText(thumbnailKey)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/devices/media/{id}/thumbnail")
                .buildAndExpand(mediaId)
                .toUriString();
    }

    private String buildDeviceExternalContentUrl(Long sourceId, String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/devices/media-sources/{id}/content")
                .queryParam("path", path)
                .buildAndExpand(sourceId)
                .toUriString();
    }

    private String buildDeviceExternalThumbnailUrl(Long sourceId, String path, String mediaType) {
        if (!"VIDEO".equals(mediaType)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/devices/media-sources/{id}/thumbnail")
                .queryParam("path", path)
                .buildAndExpand(sourceId)
                .toUriString();
    }

    private Map<Long, DeviceGroupSummary> loadGroupSummaryByDeviceId(List<Device> devices) {
        if (CollectionUtils.isEmpty(devices)) {
            return Collections.emptyMap();
        }
        List<Long> deviceIds = devices.stream().map(Device::getId).toList();
        List<DeviceGroupRel> relations = deviceGroupRelMapper.selectList(new LambdaQueryWrapper<DeviceGroupRel>()
                .in(DeviceGroupRel::getDeviceId, deviceIds));
        if (CollectionUtils.isEmpty(relations)) {
            return Collections.emptyMap();
        }
        List<Long> groupIds = relations.stream().map(DeviceGroupRel::getGroupId).distinct().toList();
        Map<Long, String> groupNameById = deviceGroupMapper.selectBatchIds(groupIds).stream()
                .collect(Collectors.toMap(DeviceGroup::getId, DeviceGroup::getName));
        Map<Long, DeviceGroupSummary> summaryByDeviceId = new HashMap<>();
        for (DeviceGroupRel relation : relations) {
            String groupName = groupNameById.get(relation.getGroupId());
            if (StringUtils.hasText(groupName)) {
                DeviceGroupSummary summary = summaryByDeviceId.computeIfAbsent(relation.getDeviceId(), ignored -> new DeviceGroupSummary());
                summary.groupIds().add(relation.getGroupId());
                summary.groupNames().add(groupName);
            }
        }
        return summaryByDeviceId;
    }

    private DeviceResponse toDeviceResponse(Device device) {
        return toDeviceResponse(device, null);
    }

    private DeviceResponse toDeviceResponse(Device device, DeviceGroupSummary groupSummary) {
        DeviceResponse response = new DeviceResponse();
        response.setId(device.getId());
        response.setDeviceUid(device.getDeviceUid());
        response.setName(device.getName());
        response.setType(device.getType());
        response.setStatus(device.getStatus());
        response.setLastHeartbeatAt(device.getLastHeartbeatAt());
        response.setBoundAt(device.getBoundAt());
        response.setGroupIds(groupSummary == null ? Collections.emptyList() : groupSummary.groupIds());
        response.setGroupNames(groupSummary == null ? Collections.emptyList() : groupSummary.groupNames());
        response.setUpdatedAt(device.getUpdatedAt());
        return response;
    }

    private record DeviceGroupSummary(List<Long> groupIds, List<String> groupNames) {
        private DeviceGroupSummary() {
            this(new ArrayList<>(), new ArrayList<>());
        }
    }

    private DeviceGroupResponse toGroupResponse(DeviceGroup group) {
        DeviceGroupResponse response = new DeviceGroupResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setDescription(group.getDescription());
        response.setCreatedAt(group.getCreatedAt());
        response.setUpdatedAt(group.getUpdatedAt());
        Long deviceCount = deviceGroupRelMapper.selectCount(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getGroupId, group.getId()));
        response.setDeviceCount(deviceCount == null ? 0L : Long.valueOf(deviceCount.intValue()));
        return response;
    }

    private Device getOwnedDevice(Long userId, Long deviceId) {
        Device device = getDevice(deviceId);
        if (!Objects.equals(device.getUserId(), userId)) {
            throw new BusinessException(ResultCode.DEVICE_ACCESS_DENIED);
        }
        return device;
    }

    private Device getOwnedDeviceByUid(Long userId, String deviceUid) {
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceUid, deviceUid)
                .last("limit 1"));
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (!Objects.equals(device.getUserId(), userId)) {
            throw new BusinessException(ResultCode.DEVICE_ACCESS_DENIED);
        }
        return device;
    }

    private Device getDevice(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        return device;
    }

    private DeviceGroup getOwnedGroup(Long userId, Long groupId) {
        DeviceGroup group = deviceGroupMapper.selectById(groupId);
        if (group == null || !Objects.equals(group.getUserId(), userId)) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
        }
        return group;
    }

    private DeviceStatus parseDeviceStatus(String status) {
        try {
            return DeviceStatus.valueOf(status);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法设备状态: " + status);
        }
    }

    private void ensureDeviceBound(Device device) {
        if (device.getUserId() == null || DeviceStatus.UNBOUND.name().equals(device.getStatus())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_BOUND);
        }
    }

    @SafeVarargs
    private <T> T firstNonNull(T... candidates) {
        if (candidates == null) {
            return null;
        }
        for (T candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String firstText(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void storeDevicePullSnapshot(DevicePullSnapshot snapshot) {
        String key = devicePullSnapshotKey(snapshot.getSnapshotId());
        Map<String, Object> value = new HashMap<>();
        value.put("snapshotId", snapshot.getSnapshotId());
        value.put("userId", snapshot.getUserId());
        value.put("deviceTokenAccess", snapshot.isDeviceTokenAccess());
        value.put("createdAt", snapshot.getCreatedAt().toString());
        value.put("orderedDistributionIds", snapshot.getOrderedDistributionIds());
        devicePullRedisTemplate.opsForValue().set(key, value, Duration.ofMinutes(5));
    }

    private void refreshDevicePullSnapshotTtl(String snapshotId) {
        devicePullRedisTemplate.expire(devicePullSnapshotKey(snapshotId), Duration.ofMinutes(5));
    }

    private void deleteDevicePullSnapshot(String snapshotId) {
        devicePullRedisTemplate.delete(devicePullSnapshotKey(snapshotId));
    }

    @SuppressWarnings("unchecked")
    private DevicePullSnapshot loadDevicePullSnapshot(String snapshotId) {
        Object value = devicePullRedisTemplate.opsForValue().get(devicePullSnapshotKey(snapshotId));
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        DevicePullSnapshot snapshot = new DevicePullSnapshot();
        snapshot.setSnapshotId(String.valueOf(map.get("snapshotId")));
        snapshot.setUserId(toLong(map.get("userId")));
        snapshot.setDeviceTokenAccess(Boolean.TRUE.equals(map.get("deviceTokenAccess")));
        snapshot.setCreatedAt(LocalDateTime.parse(String.valueOf(map.get("createdAt"))));
        Object ids = map.get("orderedDistributionIds");
        if (ids instanceof List<?> list) {
            snapshot.setOrderedDistributionIds(list.stream().map(this::toLong).toList());
        } else {
            snapshot.setOrderedDistributionIds(Collections.emptyList());
        }
        return snapshot;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.parseLong(text);
        }
        return null;
    }

    private PullChunkCursor parsePullChunkCursor(String cursor) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor));
            Map<?, ?> map = devicePullObjectMapper.readValue(json, Map.class);
            String snapshotId = map.get("snapshotId") == null ? null : String.valueOf(map.get("snapshotId"));
            int nextDistributionIndex = map.get("nextDistributionIndex") == null ? 0 : ((Number) map.get("nextDistributionIndex")).intValue();
            int returnedMediaCount = map.get("returnedMediaCount") == null ? 0 : ((Number) map.get("returnedMediaCount")).intValue();
            if (!StringUtils.hasText(snapshotId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "非法拉取游标");
            }
            return new PullChunkCursor(snapshotId, nextDistributionIndex, returnedMediaCount);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法拉取游标");
        }
    }

    private String encodePullChunkCursor(PullChunkCursor cursor) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("snapshotId", cursor.snapshotId());
            map.put("nextDistributionIndex", cursor.nextDistributionIndex());
            map.put("returnedMediaCount", cursor.returnedMediaCount());
            String json = devicePullObjectMapper.writeValueAsString(map);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "生成拉取游标失败");
        }
    }

    private String devicePullSnapshotKey(String snapshotId) {
        return "device:pull:snapshot:" + snapshotId;
    }

    private record PullChunkCursor(String snapshotId, int nextDistributionIndex, int returnedMediaCount) {}

    private static class DevicePullSnapshot {

        private String snapshotId;
        private Long userId;
        private boolean deviceTokenAccess;
        private LocalDateTime createdAt;
        private List<Long> orderedDistributionIds = Collections.emptyList();

        public String getSnapshotId() {
            return snapshotId;
        }

        public void setSnapshotId(String snapshotId) {
            this.snapshotId = snapshotId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public boolean isDeviceTokenAccess() {
            return deviceTokenAccess;
        }

        public void setDeviceTokenAccess(boolean deviceTokenAccess) {
            this.deviceTokenAccess = deviceTokenAccess;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public List<Long> getOrderedDistributionIds() {
            return orderedDistributionIds;
        }

        public void setOrderedDistributionIds(List<Long> orderedDistributionIds) {
            this.orderedDistributionIds = orderedDistributionIds;
        }
    }
}
