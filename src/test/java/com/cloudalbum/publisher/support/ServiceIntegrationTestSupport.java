package com.cloudalbum.publisher.support;

import com.cloudalbum.publisher.CloudAlbumPublisherApplication;
import com.cloudalbum.publisher.album.entity.Album;
import com.cloudalbum.publisher.album.entity.AlbumMedia;
import com.cloudalbum.publisher.album.mapper.AlbumMapper;
import com.cloudalbum.publisher.album.mapper.AlbumMediaMapper;
import com.cloudalbum.publisher.device.entity.Device;
import com.cloudalbum.publisher.device.entity.DeviceGroup;
import com.cloudalbum.publisher.device.entity.DeviceGroupRel;
import com.cloudalbum.publisher.device.mapper.DeviceGroupMapper;
import com.cloudalbum.publisher.device.mapper.DeviceGroupRelMapper;
import com.cloudalbum.publisher.device.mapper.DeviceMapper;
import com.cloudalbum.publisher.device.service.DeviceService;
import com.cloudalbum.publisher.distribution.dto.DistributionCreateRequest;
import com.cloudalbum.publisher.distribution.entity.Distribution;
import com.cloudalbum.publisher.distribution.entity.DistributionDevice;
import com.cloudalbum.publisher.distribution.mapper.DistributionDeviceMapper;
import com.cloudalbum.publisher.distribution.mapper.DistributionMapper;
import com.cloudalbum.publisher.distribution.service.DistributionService;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.review.entity.ReviewRecord;
import com.cloudalbum.publisher.review.mapper.ReviewRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest(classes = CloudAlbumPublisherApplication.class)
@ActiveProfiles("test")
@Transactional
public abstract class ServiceIntegrationTestSupport {

    protected static final Long USER_ID = 1L;
    protected static final Long OTHER_USER_ID = 2L;

    @Autowired
    protected DistributionService distributionService;

    @Autowired
    protected DeviceService deviceService;

    @Autowired
    protected AlbumMapper albumMapper;

    @Autowired
    protected AlbumMediaMapper albumMediaMapper;

    @Autowired
    protected MediaMapper mediaMapper;

    @Autowired
    protected DeviceMapper deviceMapper;

    @Autowired
    protected DeviceGroupMapper deviceGroupMapper;

    @Autowired
    protected DeviceGroupRelMapper deviceGroupRelMapper;

    @Autowired
    protected DistributionMapper distributionMapper;

    @Autowired
    protected DistributionDeviceMapper distributionDeviceMapper;

    @Autowired
    protected ReviewRecordMapper reviewRecordMapper;

    protected Album insertAlbum(Long userId, String visibility) {
        Album album = new Album();
        album.setUserId(userId);
        album.setTitle("album-" + visibility + "-" + System.nanoTime());
        album.setDescription("test album");
        album.setCoverUrl("covers/album.jpg");
        album.setBgmUrl("bgm/test.mp3");
        album.setBgmVolume(80);
        album.setTransitionStyle("NONE");
        album.setVisibility(visibility);
        album.setStatus("DRAFT");
        album.setSortOrder(0);
        albumMapper.insert(album);
        return album;
    }

    protected Media insertMedia(Long userId, String status, String fileName) {
        Media media = new Media();
        media.setUserId(userId);
        media.setFileName(fileName);
        media.setContentType("image/jpeg");
        media.setMediaType("IMAGE");
        media.setFileSize(1024L);
        media.setBucketName("test-bucket");
        media.setObjectKey("objects/" + fileName);
        media.setThumbnailKey("thumbs/" + fileName + ".jpg");
        media.setDurationSec(15);
        media.setWidth(1920);
        media.setHeight(1080);
        media.setStatus(status);
        mediaMapper.insert(media);
        return media;
    }

    protected AlbumMedia insertAlbumMedia(Long albumId, Long mediaId, int sortOrder, Integer duration) {
        AlbumMedia albumMedia = new AlbumMedia();
        albumMedia.setAlbumId(albumId);
        albumMedia.setMediaId(mediaId);
        albumMedia.setSortOrder(sortOrder);
        albumMedia.setDuration(duration);
        albumMediaMapper.insert(albumMedia);
        return albumMedia;
    }

    protected ReviewRecord insertReview(Long mediaId, Long userId, String status, LocalDateTime updatedAt) {
        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setMediaId(mediaId);
        reviewRecord.setUserId(userId);
        reviewRecord.setReviewerId(999L);
        reviewRecord.setStatus(status);
        reviewRecord.setReviewedAt(updatedAt);
        reviewRecord.setCreatedAt(updatedAt);
        reviewRecord.setUpdatedAt(updatedAt);
        reviewRecordMapper.insert(reviewRecord);
        return reviewRecord;
    }

    protected Device insertDevice(Long userId, String deviceUid) {
        Device device = new Device();
        device.setUserId(userId);
        device.setDeviceUid(deviceUid);
        device.setName("device-" + deviceUid);
        device.setType("TV");
        device.setStatus("OFFLINE");
        device.setLastHeartbeatAt(LocalDateTime.now().minusMinutes(10));
        device.setBoundAt(LocalDateTime.now().minusMinutes(10));
        deviceMapper.insert(device);
        return device;
    }

    protected DeviceGroup insertGroup(Long userId, String name) {
        DeviceGroup group = new DeviceGroup();
        group.setUserId(userId);
        group.setName(name);
        group.setDescription("test group");
        deviceGroupMapper.insert(group);
        return group;
    }

    protected DeviceGroupRel insertGroupMember(Long groupId, Long deviceId) {
        DeviceGroupRel rel = new DeviceGroupRel();
        rel.setGroupId(groupId);
        rel.setDeviceId(deviceId);
        deviceGroupRelMapper.insert(rel);
        return rel;
    }

    protected Distribution insertDistribution(Long userId, Long albumId, String status, String name, Integer itemDuration) {
        Distribution distribution = new Distribution();
        distribution.setUserId(userId);
        distribution.setAlbumId(albumId);
        distribution.setName(name);
        distribution.setLoopPlay(true);
        distribution.setShuffle(false);
        distribution.setItemDuration(itemDuration);
        distribution.setStatus(status);
        distributionMapper.insert(distribution);
        return distribution;
    }

    protected DistributionDevice insertDistributionDeviceTarget(Long distributionId, Long deviceId) {
        DistributionDevice relation = new DistributionDevice();
        relation.setDistributionId(distributionId);
        relation.setDeviceId(deviceId);
        distributionDeviceMapper.insert(relation);
        return relation;
    }

    protected DistributionDevice insertDistributionGroupTarget(Long distributionId, Long groupId) {
        DistributionDevice relation = new DistributionDevice();
        relation.setDistributionId(distributionId);
        relation.setGroupId(groupId);
        distributionDeviceMapper.insert(relation);
        return relation;
    }

    protected DistributionCreateRequest newDistributionRequest(Long albumId, List<Long> deviceIds, List<Long> groupIds) {
        DistributionCreateRequest request = new DistributionCreateRequest();
        request.setAlbumId(albumId);
        request.setName("distribution-" + System.nanoTime());
        request.setLoopPlay(true);
        request.setShuffle(false);
        request.setItemDuration(10);
        request.setDeviceIds(deviceIds);
        request.setGroupIds(groupIds);
        return request;
    }
}
