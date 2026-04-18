package com.cloudalbum.publisher.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.album.entity.Album;
import com.cloudalbum.publisher.album.entity.AlbumMedia;
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
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                        .eq(Device::getUserId, userId)
                        .orderByDesc(Device::getUpdatedAt))
                .stream().map(this::toDeviceResponse).toList();
    }

    @Override
    @Transactional
    public DeviceResponse bindDevice(Long userId, DeviceBindRequest request) {
        Device exists = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceUid, request.getDeviceUid())
                .last("limit 1"));

        if (exists != null && !Objects.equals(exists.getUserId(), userId)) {
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

        if (exists == null) {
            deviceMapper.insert(device);
        } else {
            deviceMapper.updateById(device);
        }
        return toDeviceResponse(device);
    }

    @Override
    @Transactional
    public DeviceTokenResponse createAccessToken(Long userId, DeviceTokenRequest request) {
        Device device = getOwnedDeviceByUid(userId, request.getDeviceUid());
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
        device.setStatus(DeviceStatus.UNBOUND.name());
        device.setUnboundAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        deviceMapper.deleteById(deviceId);

        deviceGroupRelMapper.delete(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getDeviceId, deviceId));
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
        return buildPullResponse(getDevice(deviceId), true);
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
        getOwnedDevice(userId, deviceId);

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
        resolveExternalMediaItem(sourceId, device.getUserId(), path, null);
        mediaSourceService.writeMediaContent(sourceId, device.getUserId(), path, thumbnail, request, response);
    }

    private DevicePullResponse buildPullResponse(Device device, boolean deviceTokenAccess) {
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

        List<DeviceGroupRel> groupRelations = deviceGroupRelMapper.selectList(
                new LambdaQueryWrapper<DeviceGroupRel>()
                        .eq(DeviceGroupRel::getDeviceId, deviceId));
        if (!CollectionUtils.isEmpty(groupRelations)) {
            List<Long> groupIds = groupRelations.stream()
                    .map(DeviceGroupRel::getGroupId)
                    .distinct()
                    .toList();
            if (!CollectionUtils.isEmpty(groupIds)) {
                List<DistributionDevice> groupedRelations = distributionDeviceMapper.selectList(
                        new LambdaQueryWrapper<DistributionDevice>()
                                .in(DistributionDevice::getGroupId, groupIds));
                groupedRelations.stream()
                        .map(DistributionDevice::getDistributionId)
                        .forEach(distributionIds::add);
            }
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

        List<AlbumMedia> albumMediaList = albumMediaMapper.selectList(
                new LambdaQueryWrapper<AlbumMedia>()
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
        Map<Long, Media> mediaMap = mediaIds.isEmpty()
                ? Collections.emptyMap()
                : mediaMapper.selectBatchIds(mediaIds).stream()
                .collect(Collectors.toMap(Media::getId, Function.identity()));
        Map<Long, ReviewRecord> latestReviewMap = queryLatestReviewMap(mediaIds);

        List<DevicePullResponse.MediaItem> mediaItems = albumMediaList.stream()
                .map(albumMedia -> toPullMediaItem(albumMedia,
                        mediaMap.get(albumMedia.getMediaId()),
                        latestReviewMap.get(albumMedia.getMediaId()),
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
        if (!StringUtils.hasText(album.getVisibility())) {
            return true;
        }
        if ("PRIVATE".equals(album.getVisibility())) {
            return false;
        }
        if ("DEVICE_ONLY".equals(album.getVisibility())) {
            return deviceTokenAccess;
        }
        return true;
    }

    private DevicePullResponse.AlbumItem toPullAlbumItem(Album album) {
        DevicePullResponse.AlbumItem item = new DevicePullResponse.AlbumItem();
        item.setId(album.getId());
        item.setTitle(album.getTitle());
        item.setDescription(album.getDescription());
        item.setCoverUrl(buildAlbumCoverUrl(album));
        item.setBgmUrl(buildAlbumBgmUrl(album));
        item.setBgmVolume(album.getBgmVolume());
        item.setVisibility(album.getVisibility());
        return item;
    }

    private String buildAlbumBgmUrl(Album album) {
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
        item.setId(albumMedia.getId());
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
        if (albumMedia.getDuration() != null && albumMedia.getDuration() > 0) {
            return albumMedia.getDuration();
        }
        return distributionItemDuration;
    }

    private Map<Long, ReviewRecord> queryLatestReviewMap(List<Long> mediaIds) {
        if (CollectionUtils.isEmpty(mediaIds)) {
            return Collections.emptyMap();
        }
        List<ReviewRecord> reviewRecords = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<ReviewRecord>()
                        .in(ReviewRecord::getMediaId, mediaIds)
                        .orderByDesc(ReviewRecord::getUpdatedAt)
                        .orderByDesc(ReviewRecord::getId));
        Map<Long, ReviewRecord> latestReviewMap = new HashMap<>();
        for (ReviewRecord reviewRecord : reviewRecords) {
            latestReviewMap.putIfAbsent(reviewRecord.getMediaId(), reviewRecord);
        }
        return latestReviewMap;
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
        MediaSourceBrowseResponse browseResponse = mediaSourceService.browse(sourceId, userId, parentPath);
        MediaSourceBrowseItemResponse item = browseResponse.getItems().stream()
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getDirectory()))
                .filter(candidate -> normalizedPath.equals(normalizeExternalPath(candidate.getPath())))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "外部媒体不存在或不可访问"));
        if (!StringUtils.hasText(item.getExternalMediaKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前外部文件不支持设备播放");
        }
        if (StringUtils.hasText(externalMediaKey) && !externalMediaKey.equals(item.getExternalMediaKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "外部媒体引用已失效，请重新选择");
        }
        if (!StringUtils.hasText(item.getMediaType()) || "OTHER".equals(item.getMediaType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持播放图片、视频或音频文件");
        }
        return item;
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

    private Device getOwnedDevice(Long userId, Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (!Objects.equals(device.getUserId(), userId)) {
            throw new BusinessException(ResultCode.DEVICE_ACCESS_DENIED);
        }
        return device;
    }

    private Device getOwnedDeviceByUid(Long userId, String deviceUid) {
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getUserId, userId)
                .eq(Device::getDeviceUid, deviceUid)
                .last("limit 1"));
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
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
        if (group == null) {
            throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
        }
        if (!Objects.equals(group.getUserId(), userId)) {
            throw new BusinessException(ResultCode.DEVICE_ACCESS_DENIED);
        }
        return group;
    }

    private DeviceStatus parseDeviceStatus(String status) {
        try {
            return DeviceStatus.valueOf(status.toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效设备状态: " + status);
        }
    }

    private DeviceResponse toDeviceResponse(Device device) {
        DeviceResponse response = new DeviceResponse();
        response.setId(device.getId());
        response.setDeviceUid(device.getDeviceUid());
        response.setName(device.getName());
        response.setType(device.getType());
        response.setStatus(device.getStatus());
        response.setLastHeartbeatAt(device.getLastHeartbeatAt());
        response.setBoundAt(device.getBoundAt());
        response.setUpdatedAt(device.getUpdatedAt());
        return response;
    }

    private DeviceGroupResponse toGroupResponse(DeviceGroup group) {
        Long count = deviceGroupRelMapper.selectCount(new LambdaQueryWrapper<DeviceGroupRel>()
                .eq(DeviceGroupRel::getGroupId, group.getId()));
        DeviceGroupResponse response = new DeviceGroupResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setDescription(group.getDescription());
        response.setDeviceCount(count == null ? 0L : count);
        response.setCreatedAt(group.getCreatedAt());
        response.setUpdatedAt(group.getUpdatedAt());
        return response;
    }

    private String buildDeviceContentUrl(Long mediaId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/devices/media/{id}/content")
                .buildAndExpand(mediaId)
                .toUriString();
    }

    private String buildDeviceExternalContentUrl(Long sourceId, String path) {
        if (sourceId == null || !StringUtils.hasText(path)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/devices/media-sources/{id}/content")
                .queryParam("path", path)
                .buildAndExpand(sourceId)
                .toUriString();
    }

    private String buildAlbumCoverUrl(Album album) {
        if (album == null) {
            return null;
        }
        if (album.getCoverMediaId() == null && !StringUtils.hasText(album.getCoverUrl()) && album.getCoverSourceId() == null) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/devices/albums/{id}/cover")
                .buildAndExpand(album.getId())
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

    private String buildDeviceExternalThumbnailUrl(Long sourceId, String path, String mediaType) {
        if (sourceId == null || !StringUtils.hasText(path) || !"IMAGE".equals(mediaType)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/devices/media-sources/{id}/thumbnail")
                .queryParam("path", path)
                .buildAndExpand(sourceId)
                .toUriString();
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
        return media != null && StringUtils.hasText(media.getThumbnailKey());
    }

    private boolean shouldUseExternalThumbnailForCover(Album album) {
        return "IMAGE".equals(album.getCoverMediaType());
    }

    private boolean hasExternalCover(Album album) {
        return album.getCoverSourceId() != null && StringUtils.hasText(album.getCoverPath());
    }

    private String resolveCoverObjectKey(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return null;
        }
        if (!coverUrl.contains("://") && !coverUrl.startsWith("/")) {
            return coverUrl;
        }
        try {
            URI uri = URI.create(coverUrl);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            String bucketPrefix = bucket + "/";
            if (normalizedPath.startsWith(bucketPrefix)) {
                return normalizedPath.substring(bucketPrefix.length());
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstText(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return fallback;
    }

    private <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }
}
