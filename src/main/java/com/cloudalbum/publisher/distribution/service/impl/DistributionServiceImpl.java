package com.cloudalbum.publisher.distribution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudalbum.publisher.album.entity.Album;
import com.cloudalbum.publisher.album.entity.AlbumMedia;
import com.cloudalbum.publisher.album.mapper.AlbumMapper;
import com.cloudalbum.publisher.album.mapper.AlbumMediaMapper;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.device.entity.Device;
import com.cloudalbum.publisher.device.entity.DeviceGroup;
import com.cloudalbum.publisher.device.entity.DeviceGroupRel;
import com.cloudalbum.publisher.device.mapper.DeviceGroupMapper;
import com.cloudalbum.publisher.device.mapper.DeviceGroupRelMapper;
import com.cloudalbum.publisher.device.mapper.DeviceMapper;
import com.cloudalbum.publisher.distribution.dto.DistributionCreateRequest;
import com.cloudalbum.publisher.distribution.dto.DistributionResponse;
import com.cloudalbum.publisher.distribution.dto.DistributionUpdateRequest;
import com.cloudalbum.publisher.distribution.entity.Distribution;
import com.cloudalbum.publisher.distribution.entity.DistributionDevice;
import com.cloudalbum.publisher.distribution.mapper.DistributionDeviceMapper;
import com.cloudalbum.publisher.distribution.mapper.DistributionMapper;
import com.cloudalbum.publisher.distribution.service.DistributionService;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseItemResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseResponse;
import com.cloudalbum.publisher.mediasource.service.MediaSourceService;
import com.cloudalbum.publisher.review.entity.ReviewRecord;
import com.cloudalbum.publisher.review.mapper.ReviewRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DistributionServiceImpl implements DistributionService {

    private final DistributionMapper distributionMapper;
    private final DistributionDeviceMapper distributionDeviceMapper;
    private final AlbumMapper albumMapper;
    private final AlbumMediaMapper albumMediaMapper;
    private final MediaMapper mediaMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceGroupMapper deviceGroupMapper;
    private final DeviceGroupRelMapper deviceGroupRelMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final MediaSourceService mediaSourceService;

    @Override
    @Transactional
    public DistributionResponse createDistribution(Long userId, DistributionCreateRequest request) {
        Distribution dist = new Distribution();
        dist.setAlbumId(request.getAlbumId());
        dist.setUserId(userId);
        dist.setName(request.getName());
        dist.setLoopPlay(request.getLoopPlay());
        dist.setShuffle(request.getShuffle());
        dist.setItemDuration(request.getItemDuration());
        dist.setStatus("DRAFT");
        distributionMapper.insert(dist);

        saveTargets(dist.getId(), request.getDeviceIds(), request.getGroupIds());
        return toResponse(dist, queryDeviceIds(dist.getId()), queryGroupIds(dist.getId()));
    }

    @Override
    public PageResult<DistributionResponse> listDistributions(Long userId, PageRequest pageRequest, String status) {
        LambdaQueryWrapper<Distribution> queryWrapper = new LambdaQueryWrapper<Distribution>()
                .eq(Distribution::getUserId, userId)
                .orderByDesc(Distribution::getCreatedAt);
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Distribution::getStatus, status);
        }

        Page<Distribution> page = new Page<>(pageRequest.getPage(), pageRequest.getSize());
        Page<Distribution> result = distributionMapper.selectPage(page, queryWrapper);
        return PageResult.of(result.convert(dist -> toResponse(dist, queryDeviceIds(dist.getId()), queryGroupIds(dist.getId()))));
    }

    @Override
    public DistributionResponse getDistribution(Long id, Long userId) {
        Distribution dist = getAndCheckOwner(id, userId);
        return toResponse(dist, queryDeviceIds(id), queryGroupIds(id));
    }

    @Override
    @Transactional
    public DistributionResponse updateDistribution(Long id, Long userId, DistributionUpdateRequest request) {
        Distribution dist = getAndCheckOwner(id, userId);

        if (request.getName() != null) dist.setName(request.getName());
        if (request.getLoopPlay() != null) dist.setLoopPlay(request.getLoopPlay());
        if (request.getShuffle() != null) dist.setShuffle(request.getShuffle());
        if (request.getItemDuration() != null) dist.setItemDuration(request.getItemDuration());
        distributionMapper.updateById(dist);

        List<Long> deviceIds = request.getDeviceIds();
        List<Long> groupIds = request.getGroupIds();
        if (deviceIds != null || groupIds != null) {
            distributionDeviceMapper.delete(
                    new LambdaQueryWrapper<DistributionDevice>()
                            .eq(DistributionDevice::getDistributionId, id));
            saveTargets(id, deviceIds, groupIds);
        } else {
            deviceIds = queryDeviceIds(id);
            groupIds = queryGroupIds(id);
        }
        return toResponse(dist, deviceIds, groupIds);
    }

    @Override
    @Transactional
    public void deleteDistribution(Long id, Long userId) {
        getAndCheckOwner(id, userId);
        distributionMapper.deleteById(id);
        distributionDeviceMapper.delete(
                new LambdaQueryWrapper<DistributionDevice>()
                        .eq(DistributionDevice::getDistributionId, id));
    }

    @Override
    @Transactional
    public DistributionResponse activateDistribution(Long id, Long userId) {
        Distribution dist = getAndCheckOwner(id, userId);
        if ("ACTIVE".equals(dist.getStatus())) {
            throw new BusinessException(ResultCode.DISTRIBUTION_ALREADY_ACTIVE);
        }

        List<Long> deviceIds = queryDeviceIds(id);
        List<Long> groupIds = queryGroupIds(id);
        validateDistributionBeforeActivate(dist, userId, deviceIds, groupIds);

        dist.setStatus("ACTIVE");
        distributionMapper.updateById(dist);
        return toResponse(dist, deviceIds, groupIds);
    }

    @Override
    @Transactional
    public DistributionResponse disableDistribution(Long id, Long userId) {
        Distribution dist = getAndCheckOwner(id, userId);
        if ("DISABLED".equals(dist.getStatus())) {
            throw new BusinessException(ResultCode.DISTRIBUTION_ALREADY_DISABLED);
        }
        dist.setStatus("DISABLED");
        distributionMapper.updateById(dist);
        return toResponse(dist, queryDeviceIds(id), queryGroupIds(id));
    }

    @Override
    public List<DistributionResponse> listActiveDistributions(Long userId) {
        List<Distribution> list = distributionMapper.selectList(
                new LambdaQueryWrapper<Distribution>()
                        .eq(Distribution::getUserId, userId)
                        .eq(Distribution::getStatus, "ACTIVE")
                        .orderByDesc(Distribution::getCreatedAt));
        return list.stream()
                .map(dist -> toResponse(dist, queryDeviceIds(dist.getId()), queryGroupIds(dist.getId())))
                .collect(Collectors.toList());
    }

    private Distribution getAndCheckOwner(Long id, Long userId) {
        Distribution dist = distributionMapper.selectById(id);
        if (dist == null) {
            throw new BusinessException(ResultCode.DISTRIBUTION_NOT_FOUND);
        }
        if (!dist.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.DISTRIBUTION_ACCESS_DENIED);
        }
        return dist;
    }

    private void saveTargets(Long distributionId, List<Long> deviceIds, List<Long> groupIds) {
        saveDevices(distributionId, deviceIds);
        saveGroups(distributionId, groupIds);
    }

    private void saveDevices(Long distributionId, List<Long> deviceIds) {
        if (CollectionUtils.isEmpty(deviceIds)) return;
        for (Long deviceId : deviceIds) {
            DistributionDevice dd = new DistributionDevice();
            dd.setDistributionId(distributionId);
            dd.setDeviceId(deviceId);
            distributionDeviceMapper.insert(dd);
        }
    }

    private void saveGroups(Long distributionId, List<Long> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) return;
        for (Long groupId : groupIds) {
            DistributionDevice dd = new DistributionDevice();
            dd.setDistributionId(distributionId);
            dd.setGroupId(groupId);
            distributionDeviceMapper.insert(dd);
        }
    }

    private List<Long> queryDeviceIds(Long distributionId) {
        List<DistributionDevice> devices = distributionDeviceMapper.selectList(
                new LambdaQueryWrapper<DistributionDevice>()
                        .eq(DistributionDevice::getDistributionId, distributionId)
                        .isNotNull(DistributionDevice::getDeviceId));
        if (CollectionUtils.isEmpty(devices)) return Collections.emptyList();
        return devices.stream().map(DistributionDevice::getDeviceId).collect(Collectors.toList());
    }

    private List<Long> queryGroupIds(Long distributionId) {
        List<DistributionDevice> groups = distributionDeviceMapper.selectList(
                new LambdaQueryWrapper<DistributionDevice>()
                        .eq(DistributionDevice::getDistributionId, distributionId)
                        .isNotNull(DistributionDevice::getGroupId));
        if (CollectionUtils.isEmpty(groups)) return Collections.emptyList();
        return groups.stream().map(DistributionDevice::getGroupId).collect(Collectors.toList());
    }

    private void validateDistributionBeforeActivate(Distribution dist, Long userId, List<Long> deviceIds, List<Long> groupIds) {
        Album album = albumMapper.selectById(dist.getAlbumId());
        if (album == null) {
            throw new BusinessException(ResultCode.ALBUM_NOT_FOUND);
        }
        if (!userId.equals(album.getUserId())) {
            throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED);
        }

        if ("PRIVATE".equals(album.getVisibility())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "私有相册不能下发到设备，请改为公开或设备专属后再激活");
        }

        validateTargets(deviceIds, groupIds, userId);

        List<AlbumMedia> albumMediaList = albumMediaMapper.selectList(
                new LambdaQueryWrapper<AlbumMedia>()
                        .eq(AlbumMedia::getAlbumId, album.getId())
                        .orderByAsc(AlbumMedia::getSortOrder)
                        .orderByAsc(AlbumMedia::getId));
        if (CollectionUtils.isEmpty(albumMediaList)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "相册中暂无可分发内容");
        }

        validateInternalMediaBeforeActivate(albumMediaList, userId);
        validateExternalMediaBeforeActivate(albumMediaList, userId);
    }

    private void validateInternalMediaBeforeActivate(List<AlbumMedia> albumMediaList, Long userId) {
        List<Long> mediaIds = albumMediaList.stream()
                .map(AlbumMedia::getMediaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(mediaIds)) {
            return;
        }
        List<Media> mediaList = mediaMapper.selectBatchIds(mediaIds);
        Map<Long, Media> mediaMap = mediaList.stream()
                .collect(Collectors.toMap(Media::getId, Function.identity()));

        for (Long mediaId : mediaIds) {
            Media media = mediaMap.get(mediaId);
            if (media == null) {
                throw new BusinessException(ResultCode.MEDIA_NOT_FOUND);
            }
            if (!userId.equals(media.getUserId())) {
                throw new BusinessException(ResultCode.MEDIA_ACCESS_DENIED);
            }
            if (!"READY".equals(media.getStatus())) {
                throw new BusinessException(ResultCode.MEDIA_NOT_READY,
                        "媒体尚未就绪: " + media.getFileName());
            }
            validateMediaReviewApproved(media);
        }
    }

    private void validateExternalMediaBeforeActivate(List<AlbumMedia> albumMediaList, Long userId) {
        List<AlbumMedia> externalItems = albumMediaList.stream()
                .filter(AlbumMedia::isExternal)
                .toList();
        if (CollectionUtils.isEmpty(externalItems)) {
            return;
        }
        for (AlbumMedia albumMedia : externalItems) {
            resolveExternalMediaItem(albumMedia.getSourceId(), userId, albumMedia.getFilePath(), albumMedia.getExternalMediaKey());
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
        MediaSourceBrowseResponse browseResponse = mediaSourceService.browse(sourceId, userId, parentPath);
        MediaSourceBrowseItemResponse item = browseResponse.getItems().stream()
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getDirectory()))
                .filter(candidate -> normalizedPath.equals(normalizeExternalPath(candidate.getPath())))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "外部媒体不存在或不可访问"));
        if (!StringUtils.hasText(item.getExternalMediaKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前外部文件不支持分发");
        }
        if (StringUtils.hasText(externalMediaKey) && !externalMediaKey.equals(item.getExternalMediaKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "外部媒体引用已失效，请重新选择");
        }
        if (!StringUtils.hasText(item.getMediaType()) || "OTHER".equals(item.getMediaType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持分发图片、视频或音频文件");
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

    private void validateTargets(List<Long> deviceIds, List<Long> groupIds, Long userId) {
        boolean hasDevices = !CollectionUtils.isEmpty(deviceIds);
        boolean hasGroups = !CollectionUtils.isEmpty(groupIds);
        if (!hasDevices && !hasGroups) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请至少选择一个分发设备或设备组");
        }
        validateDevices(deviceIds, userId);
        validateGroups(groupIds, userId);
    }

    private void validateDevices(List<Long> deviceIds, Long userId) {
        if (CollectionUtils.isEmpty(deviceIds)) {
            return;
        }

        List<Long> uniqueDeviceIds = new LinkedHashSet<>(deviceIds).stream().toList();
        List<Device> devices = deviceMapper.selectBatchIds(uniqueDeviceIds);
        Map<Long, Device> deviceMap = devices.stream()
                .collect(Collectors.toMap(Device::getId, Function.identity()));

        for (Long deviceId : uniqueDeviceIds) {
            Device device = deviceMap.get(deviceId);
            if (device == null) {
                throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
            }
            if (!userId.equals(device.getUserId())) {
                throw new BusinessException(ResultCode.DEVICE_ACCESS_DENIED);
            }
        }
    }

    private void validateGroups(List<Long> groupIds, Long userId) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return;
        }

        List<Long> uniqueGroupIds = new LinkedHashSet<>(groupIds).stream().toList();
        List<DeviceGroup> groups = deviceGroupMapper.selectBatchIds(uniqueGroupIds);
        Map<Long, DeviceGroup> groupMap = groups.stream()
                .collect(Collectors.toMap(DeviceGroup::getId, Function.identity()));

        for (Long groupId : uniqueGroupIds) {
            DeviceGroup group = groupMap.get(groupId);
            if (group == null) {
                throw new BusinessException(ResultCode.DEVICE_GROUP_NOT_FOUND);
            }
            if (!userId.equals(group.getUserId())) {
                throw new BusinessException(ResultCode.DEVICE_ACCESS_DENIED);
            }
        }
    }

    private void validateMediaReviewApproved(Media media) {
        ReviewRecord latestReview = reviewRecordMapper.selectOne(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getMediaId, media.getId())
                        .orderByDesc(ReviewRecord::getUpdatedAt)
                        .orderByDesc(ReviewRecord::getId)
                        .last("LIMIT 1"));
        if (latestReview == null || !"APPROVED".equals(latestReview.getStatus())) {
            throw new BusinessException(ResultCode.MEDIA_REVIEW_PENDING,
                    "媒体内容尚未通过审核: " + media.getFileName());
        }
    }

    private DistributionResponse toResponse(Distribution dist, List<Long> deviceIds, List<Long> groupIds) {
        return DistributionResponse.builder()
                .id(dist.getId())
                .albumId(dist.getAlbumId())
                .name(dist.getName())
                .loopPlay(dist.getLoopPlay())
                .shuffle(dist.getShuffle())
                .itemDuration(dist.getItemDuration())
                .status(dist.getStatus())
                .deviceIds(deviceIds == null ? Collections.emptyList() : deviceIds)
                .groupIds(groupIds == null ? Collections.emptyList() : groupIds)
                .createdAt(dist.getCreatedAt())
                .updatedAt(dist.getUpdatedAt())
                .build();
    }
}
