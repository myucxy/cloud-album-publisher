package com.cloudalbum.publisher.device.service;

import com.cloudalbum.publisher.device.dto.DevicePullResponse;
import com.cloudalbum.publisher.distribution.entity.Distribution;
import com.cloudalbum.publisher.support.ServiceIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeviceServiceIntegrationTest extends ServiceIntegrationTestSupport {

    @Test
    void pullContent_returnsDistributionForDirectDeviceTarget() {
        var device = insertDevice(USER_ID, "direct-device");
        var album = insertAlbum(USER_ID, "PUBLIC");
        var media = insertMedia(USER_ID, "READY", "direct-target.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, 18);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        Distribution distribution = insertDistribution(USER_ID, album.getId(), "ACTIVE", "direct-distribution", 10);
        insertDistributionDeviceTarget(distribution.getId(), device.getId());

        DevicePullResponse response = deviceService.pullContent(USER_ID, device.getDeviceUid());

        assertEquals(1, response.getDistributions().size());
        DevicePullResponse.DistributionItem item = response.getDistributions().get(0);
        assertEquals(distribution.getId(), item.getId());
        assertEquals("direct-distribution", item.getName());
        assertEquals(1, item.getMediaList().size());
        assertEquals(media.getId(), item.getMediaList().get(0).getId());
        assertEquals(18, item.getMediaList().get(0).getItemDuration());
    }

    @Test
    void pullContent_returnsDistributionForGroupTarget() {
        var device = insertDevice(USER_ID, "group-device");
        var group = insertGroup(USER_ID, "family-group");
        insertGroupMember(group.getId(), device.getId());
        var album = insertAlbum(USER_ID, "PUBLIC");
        var media = insertMedia(USER_ID, "READY", "group-target.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        Distribution distribution = insertDistribution(USER_ID, album.getId(), "ACTIVE", "group-distribution", 9);
        insertDistributionGroupTarget(distribution.getId(), group.getId());

        DevicePullResponse response = deviceService.pullContent(USER_ID, device.getDeviceUid());

        assertEquals(1, response.getDistributions().size());
        assertEquals(distribution.getId(), response.getDistributions().get(0).getId());
        assertEquals(9, response.getDistributions().get(0).getMediaList().get(0).getItemDuration());
    }

    @Test
    void pullContent_deduplicatesDistributionMatchedByDeviceAndGroup() {
        var device = insertDevice(USER_ID, "dedupe-device");
        var group = insertGroup(USER_ID, "dedupe-group");
        insertGroupMember(group.getId(), device.getId());
        var album = insertAlbum(USER_ID, "PUBLIC");
        var media = insertMedia(USER_ID, "READY", "dedupe.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        Distribution distribution = insertDistribution(USER_ID, album.getId(), "ACTIVE", "dedupe-distribution", 11);
        insertDistributionDeviceTarget(distribution.getId(), device.getId());
        insertDistributionGroupTarget(distribution.getId(), group.getId());

        DevicePullResponse response = deviceService.pullContent(USER_ID, device.getDeviceUid());

        assertEquals(1, response.getDistributions().size());
        assertEquals(distribution.getId(), response.getDistributions().get(0).getId());
    }

    @Test
    void pullContent_hidesDeviceOnlyAlbumForUserBridgeFlow() {
        var device = insertDevice(USER_ID, "device-only-user-flow");
        var album = insertAlbum(USER_ID, "DEVICE_ONLY");
        var media = insertMedia(USER_ID, "READY", "device-only-user.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        Distribution distribution = insertDistribution(USER_ID, album.getId(), "ACTIVE", "device-only-distribution", 10);
        insertDistributionDeviceTarget(distribution.getId(), device.getId());

        DevicePullResponse response = deviceService.pullContent(USER_ID, device.getDeviceUid());

        assertNotNull(response.getDistributions());
        assertTrue(response.getDistributions().isEmpty());
    }

    @Test
    void pullContentByDevice_allowsDeviceOnlyAlbumForDeviceTokenFlow() {
        var device = insertDevice(USER_ID, "device-only-device-flow");
        var album = insertAlbum(USER_ID, "DEVICE_ONLY");
        var media = insertMedia(USER_ID, "READY", "device-only-device.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        Distribution distribution = insertDistribution(USER_ID, album.getId(), "ACTIVE", "device-token-distribution", 10);
        insertDistributionDeviceTarget(distribution.getId(), device.getId());

        DevicePullResponse response = deviceService.pullContentByDevice(device.getId());

        assertEquals(1, response.getDistributions().size());
        assertEquals(distribution.getId(), response.getDistributions().get(0).getId());
        assertEquals("DEVICE_ONLY", response.getDistributions().get(0).getAlbum().getVisibility());
    }

    @Test
    void pullContent_filtersOutUnreadyOrUnapprovedMedia() {
        var device = insertDevice(USER_ID, "filter-device");
        var album = insertAlbum(USER_ID, "PUBLIC");
        var approvedReady = insertMedia(USER_ID, "READY", "approved-ready.jpg");
        var processing = insertMedia(USER_ID, "PROCESSING", "processing-filtered.jpg");
        var rejected = insertMedia(USER_ID, "READY", "rejected-filtered.jpg");
        insertAlbumMedia(album.getId(), approvedReady.getId(), 1, null);
        insertAlbumMedia(album.getId(), processing.getId(), 2, null);
        insertAlbumMedia(album.getId(), rejected.getId(), 3, null);
        insertReview(approvedReady.getId(), USER_ID, "APPROVED", LocalDateTime.now().minusMinutes(2));
        insertReview(processing.getId(), USER_ID, "APPROVED", LocalDateTime.now().minusMinutes(1));
        insertReview(rejected.getId(), USER_ID, "REJECTED", LocalDateTime.now());
        Distribution distribution = insertDistribution(USER_ID, album.getId(), "ACTIVE", "filter-distribution", 7);
        insertDistributionDeviceTarget(distribution.getId(), device.getId());

        DevicePullResponse response = deviceService.pullContent(USER_ID, device.getDeviceUid());

        assertEquals(1, response.getDistributions().size());
        List<DevicePullResponse.MediaItem> mediaList = response.getDistributions().get(0).getMediaList();
        assertEquals(1, mediaList.size());
        assertEquals(approvedReady.getId(), mediaList.get(0).getId());
        assertEquals("http://test-minio:9000/test-bucket/objects/approved-ready.jpg", mediaList.get(0).getUrl());
        assertEquals("http://test-minio:9000/test-bucket/thumbs/approved-ready.jpg.jpg", mediaList.get(0).getThumbnailUrl());
    }

    @Test
    void pullContent_updatesDeviceHeartbeatAndStatus() {
        var device = insertDevice(USER_ID, "heartbeat-device");
        LocalDateTime beforePull = device.getLastHeartbeatAt();
        var album = insertAlbum(USER_ID, "PUBLIC");
        var media = insertMedia(USER_ID, "READY", "heartbeat.jpg");
        insertAlbumMedia(album.getId(), media.getId(), 1, null);
        insertReview(media.getId(), USER_ID, "APPROVED", LocalDateTime.now());
        Distribution distribution = insertDistribution(USER_ID, album.getId(), "ACTIVE", "heartbeat-distribution", 10);
        insertDistributionDeviceTarget(distribution.getId(), device.getId());

        DevicePullResponse response = deviceService.pullContent(USER_ID, device.getDeviceUid());
        var persisted = deviceMapper.selectById(device.getId());

        assertEquals("ONLINE", response.getDevice().getStatus());
        assertEquals("ONLINE", persisted.getStatus());
        assertNotNull(persisted.getLastHeartbeatAt());
        assertTrue(persisted.getLastHeartbeatAt().isAfter(beforePull));
        assertEquals(device.getId(), response.getDevice().getId());
    }
}
