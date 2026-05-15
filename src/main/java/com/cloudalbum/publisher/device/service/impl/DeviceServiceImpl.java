package com.cloudalbum.publisher.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudalbum.publisher.album.entity.Album;
import com.cloudalbum.publisher.album.entity.AlbumBgm;
import com.cloudalbum.publisher.album.entity.AlbumMedia;
import com.cloudalbum.publisher.album.mapper.AlbumBgmMapper;
import com.cloudalbum.publisher.album.mapper.AlbumMapper;
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

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    @Value("${minio.bucket}")
    private String bucket;

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
            throw new BusinessException(ResultCode.MEDIA_NOT_FOUND);
        }
        if (!Objects.equals(media.getUserId(), device.getUserId())) {
            throw new BusinessException(ResultCode.MEDIA_ACCESS_DENIED);
        }
        if (!"READY".equals(media.getStatus())) {
            throw new BusinessException(ResultCode.MEDIA_NOT_READY);
        }
        ReviewRecord latestReview = queryLatestReviewMap(List.of(mediaId)).get(mediaId);
        if (latestReview == null || !"APPROVED".equals(latestReview.getStatus())) {
            throw new BusinessException(ResultCode.MEDIA_REVIEW_PENDING);
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
            throw new BusinessException(ResultCode.ALBUM_NOT_FOUND);
        }
        if (!Objects.equals(album.getUserId(), device.getUserId())) {
            throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED);
        }
        if (!canDeviceAccessAlbum(album, true)) {
            throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED);
        }
        if (hasExternalCover(album)) {
            mediaSourceService.writeMediaContent(
                    album.getCoverSourceId(),
                    album.getUserId(),
                    album.getCoverPath(),
                    shouldUseExternalThumbnailForCover(album),
                    request,
                    response);
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
            throw new BusinessException(ResultCode.NOT_FOUND, "相册封面不存在");
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
            throw new BusinessException(ResultCode.ALBUM_NOT_FOUND);
        }
        if (!Objects.equals(album.getUserId(), device.getUserId())) {
            throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED);
        }
        if (!canDeviceAccessAlbum(album, true)) {
            throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED);
        }
        if (hasExternalBgm(album)) {
            mediaSourceService.writeMediaContent(
                    album.getBgmSourceId(),
                    album.getUserId(),
                    album.getBgmPath(),
                    false,
                    request,
                    response);
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

        throw new BusinessException(ResultCode.NOT_FOUND, "相册BGM不存在");
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
        resolveExternalMediaItem(sourceId, device.getUserId(), path, null);
        mediaSourceService.writeMediaContent(sourceId, device.getUserId(), path, thumbnail, request, response);
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

        List<DevicePullResponse.DistributionItem> items = distributions.stream()
                .map(distribution -> toPullDistribution(distribution, device.getUserId(), deviceTokenAccess))
                .filter(Objects::nonNull)
                .toList();
        response.setDistributions(items);
        return response;
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

    private DevicePullResponse.DistributionItem toPullDistribution(Distribution distribution, Long userId, boolean deviceTokenAccess) {
        Album album = albumMapper.selectById(distribution.getAlbumId());
        if (album == null || !Objects.equals(album.getUserId(), userId)) {
            return null;
        }
        if (!canDeviceAccessAlbum(album, deviceTokenAccess)) {
            return null;
        }

        List<AlbumMedia> albumMediaList = albumMediaMapper.selectList(new LambdaQueryWrapper<AlbumMedia>()
                .eq(AlbumMedia::getAlbumId, album.getId())
                .orderByAsc(AlbumMedia::getSortOrder)
                .orderByAsc(AlbumMedia::getId));
        if (CollectionUtils.isEmpty(albumMediaList)) {
            return null;
        }

        List<Long> mediaIds = albumMediaList.stream()
                .map(AlbumMedia::getMediaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Media> mediaMap = mediaIds.isEmpty() ? Collections.emptyMap()
                : mediaMapper.selectBatchIds(mediaIds).stream().collect(Collectors.toMap(Media::getId, Function.identity()));
        Map<Long, ReviewRecord> reviewMap = queryLatestReviewMap(mediaIds);

        List<DevicePullResponse.MediaItem> mediaItems = albumMediaList.stream()
                .map(albumMedia -> toPullMediaItem(albumMedia,
                        albumMedia.getMediaId() == null ? null : mediaMap.get(albumMedia.getMediaId()),
                        albumMedia.getMediaId() == null ? null : reviewMap.get(albumMedia.getMediaId()),
                        userId,
                        distribution.getItemDuration()))
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
        item.setAlbum(toPullAlbumItem(album));
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

    private DevicePullResponse.AlbumItem toPullAlbumItem(Album album) {
        DevicePullResponse.AlbumItem item = new DevicePullResponse.AlbumItem();
        item.setId(album.getId());
        item.setTitle(album.getTitle());
        item.setDescription(album.getDescription());
        item.setCoverUrl(buildAlbumCoverUrl(album));
        List<DevicePullResponse.BgmItem> bgmList = loadAlbumBgms(album.getId()).stream()
                .map(bgm -> toPullBgmItem(bgm, album.getUserId()))
                .filter(Objects::nonNull)
                .toList();
        item.setBgmList(bgmList);
        item.setBgmUrl(!bgmList.isEmpty() ? bgmList.get(0).getUrl() : buildAlbumBgmUrl(album));
        item.setBgmVolume(album.getBgmVolume());
        item.setTransitionStyle(StringUtils.hasText(album.getTransitionStyle()) ? album.getTransitionStyle() : "NONE");
        item.setDisplayStyle(StringUtils.hasText(album.getDisplayStyle()) ? album.getDisplayStyle() : "SINGLE");
        item.setDisplayVariant(StringUtils.hasText(album.getDisplayVariant()) ? album.getDisplayVariant() : "DEFAULT");
        item.setShowTimeAndDate(Boolean.TRUE.equals(album.getShowTimeAndDate()));
        item.setVisibility(album.getVisibility());
        return item;
    }

    private DevicePullResponse.BgmItem toPullBgmItem(AlbumBgm bgm, Long userId) {
        if (bgm.isExternal()) {
            MediaSourceBrowseItemResponse externalItem = resolveExternalMediaItem(
                    bgm.getSourceId(),
                    userId,
                    bgm.getFilePath(),
                    bgm.getExternalMediaKey());
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

    private String buildAlbumBgmUrl(Album album) {
        List<AlbumBgm> bgms = loadAlbumBgms(album.getId());
        if (!bgms.isEmpty()) {
            DevicePullResponse.BgmItem first = toPullBgmItem(bgms.get(0), album.getUserId());
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
                                                         Integer distributionItemDuration) {
        if (albumMedia.isExternal()) {
            return toPullExternalMediaItem(albumMedia, userId, distributionItemDuration);
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
        return item;
    }

    private DevicePullResponse.MediaItem toPullExternalMediaItem(AlbumMedia albumMedia,
                                                                 Long userId,
                                                                 Integer distributionItemDuration) {
        MediaSourceBrowseItemResponse externalItem = resolveExternalMediaItem(
                albumMedia.getSourceId(),
                userId,
                albumMedia.getFilePath(),
                albumMedia.getExternalMediaKey());
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
        if (sourceId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "媒体源不能为空");
        }
        String normalizedPath = normalizeExternalPath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "媒体路径不能为空");
        }
        String parentPath = parentPath(normalizedPath);
        MediaSourceBrowseResponse browse = mediaSourceService.browse(sourceId, userId, parentPath);
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
}
