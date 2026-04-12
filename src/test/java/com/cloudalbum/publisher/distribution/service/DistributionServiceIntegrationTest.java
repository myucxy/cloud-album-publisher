package com.cloudalbum.publisher.distribution.service;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.distribution.dto.DistributionCreateRequest;
import com.cloudalbum.publisher.distribution.dto.DistributionResponse;
import com.cloudalbum.publisher.distribution.entity.Distribution;
import com.cloudalbum.publisher.support.ServiceIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DistributionServiceIntegrationTest extends ServiceIntegrationTestSupport {

    @Test
    void activateDistribution_requiresAtLeastOneTarget() {
        var album = insertAlbum(USER_ID, "PUBLIC");
        var media = insertMedia(USER_ID, "READY", "ready-no-target.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, 12);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());

        DistributionCreateRequest request = newDistributionRequest(album.getId(), null, null);
        DistributionResponse created = distributionService.createDistribution(USER_ID, request);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> distributionService.activateDistribution(created.getId(), USER_ID));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("请至少选择一个分发设备或设备组"));
        assertEquals("DRAFT", distributionMapper.selectById(created.getId()).getStatus());
    }

    @Test
    void activateDistribution_rejectsWhenAlbumHasNoContent() {
        var album = insertAlbum(USER_ID, "PUBLIC");
        var device = insertDevice(USER_ID, "device-empty-album");

        DistributionResponse created = distributionService.createDistribution(
                USER_ID,
                newDistributionRequest(album.getId(), List.of(device.getId()), null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> distributionService.activateDistribution(created.getId(), USER_ID));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("相册中暂无可分发内容"));
    }

    @Test
    void activateDistribution_rejectsWhenMediaNotReady() {
        var album = insertAlbum(USER_ID, "PUBLIC");
        var device = insertDevice(USER_ID, "device-media-not-ready");
        var media = insertMedia(USER_ID, "PROCESSING", "processing.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, 8);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());

        DistributionResponse created = distributionService.createDistribution(
                USER_ID,
                newDistributionRequest(album.getId(), List.of(device.getId()), null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> distributionService.activateDistribution(created.getId(), USER_ID));

        assertEquals(ResultCode.MEDIA_NOT_READY.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("媒体尚未就绪"));
        assertTrue(exception.getMessage().contains("processing.jpg"));
    }

    @Test
    void activateDistribution_rejectsWhenLatestReviewNotApproved() {
        var album = insertAlbum(USER_ID, "PUBLIC");
        var device = insertDevice(USER_ID, "device-review-pending");
        var media = insertMedia(USER_ID, "READY", "review-check.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now().minusMinutes(5));
        insertReview(media.getId(), USER_ID, "REJECTED", LocalDateTime.now());

        DistributionResponse created = distributionService.createDistribution(
                USER_ID,
                newDistributionRequest(album.getId(), List.of(device.getId()), null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> distributionService.activateDistribution(created.getId(), USER_ID));

        assertEquals(ResultCode.MEDIA_REVIEW_PENDING.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("媒体内容尚未通过审核"));
        assertTrue(exception.getMessage().contains("review-check.jpg"));
    }

    @Test
    void activateDistribution_allowsGroupOnlyTarget() {
        var album = insertAlbum(USER_ID, "PUBLIC");
        var media = insertMedia(USER_ID, "READY", "group-only.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        var group = insertGroup(USER_ID, "living-room-group");

        DistributionResponse created = distributionService.createDistribution(
                USER_ID,
                newDistributionRequest(album.getId(), null, List.of(group.getId())));

        DistributionResponse activated = distributionService.activateDistribution(created.getId(), USER_ID);
        Distribution persisted = distributionMapper.selectById(created.getId());

        assertEquals("ACTIVE", activated.getStatus());
        assertEquals("ACTIVE", persisted.getStatus());
        assertTrue(activated.getDeviceIds().isEmpty());
        assertEquals(List.of(group.getId()), activated.getGroupIds());
    }

    @Test
    void activateDistribution_rejectsForeignDeviceTarget() {
        var album = insertAlbum(USER_ID, "PUBLIC");
        var media = insertMedia(USER_ID, "READY", "foreign-device.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        var foreignDevice = insertDevice(OTHER_USER_ID, "foreign-device-uid");

        DistributionResponse created = distributionService.createDistribution(
                USER_ID,
                newDistributionRequest(album.getId(), List.of(foreignDevice.getId()), null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> distributionService.activateDistribution(created.getId(), USER_ID));

        assertEquals(ResultCode.DEVICE_ACCESS_DENIED.getCode(), exception.getCode());
    }

    @Test
    void activateDistribution_rejectsForeignGroupTarget() {
        var album = insertAlbum(USER_ID, "PUBLIC");
        var media = insertMedia(USER_ID, "READY", "foreign-group.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        var foreignGroup = insertGroup(OTHER_USER_ID, "foreign-group");

        DistributionResponse created = distributionService.createDistribution(
                USER_ID,
                newDistributionRequest(album.getId(), null, List.of(foreignGroup.getId())));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> distributionService.activateDistribution(created.getId(), USER_ID));

        assertEquals(ResultCode.DEVICE_ACCESS_DENIED.getCode(), exception.getCode());
    }
}
