package com.cloudalbum.publisher.media.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.common.enums.MediaStatus;
import com.cloudalbum.publisher.common.enums.TaskStatus;
import com.cloudalbum.publisher.media.entity.MediaProcessTask;
import com.cloudalbum.publisher.review.entity.ReviewRecord;
import com.cloudalbum.publisher.support.ServiceIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MediaTaskExecutorIntegrationTest extends ServiceIntegrationTestSupport {

    @Autowired
    private MediaTaskExecutor mediaTaskExecutor;

    @Autowired
    private com.cloudalbum.publisher.media.mapper.MediaProcessTaskMapper mediaProcessTaskMapper;

    @Test
    void executeTask_marksOtherMediaReadyAndCreatesPendingReview() {
        var media = insertMedia(USER_ID, MediaStatus.UPLOADED.name(), "executor-success.bin");
        media.setMediaType("OTHER");
        media.setThumbnailKey(null);
        mediaMapper.updateById(media);
        MediaProcessTask task = insertMediaTask(media.getId(), USER_ID, TaskStatus.PENDING.name(), 0, 3);

        mediaTaskExecutor.executeTask(task.getId());

        var persistedTask = mediaProcessTaskMapper.selectById(task.getId());
        var persistedMedia = mediaMapper.selectById(media.getId());
        List<ReviewRecord> reviewRecords = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getMediaId, media.getId()));

        assertEquals(TaskStatus.SUCCESS.name(), persistedTask.getStatus());
        assertNotNull(persistedTask.getStartedAt());
        assertNotNull(persistedTask.getFinishedAt());
        assertNull(persistedTask.getErrorMessage());
        assertEquals(MediaStatus.READY.name(), persistedMedia.getStatus());
        assertNull(persistedMedia.getErrorMessage());
        assertEquals(1, reviewRecords.size());
        assertEquals("PENDING", reviewRecords.get(0).getStatus());
    }

    @Test
    void executeTask_doesNotCreateDuplicatePendingReview() {
        var media = insertMedia(USER_ID, MediaStatus.UPLOADED.name(), "executor-review.bin");
        media.setMediaType("OTHER");
        mediaMapper.updateById(media);
        insertReview(media.getId(), USER_ID, "PENDING", LocalDateTime.now().minusMinutes(1));
        MediaProcessTask task = insertMediaTask(media.getId(), USER_ID, TaskStatus.PENDING.name(), 0, 3);

        mediaTaskExecutor.executeTask(task.getId());

        List<ReviewRecord> reviewRecords = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getMediaId, media.getId()));

        assertEquals(1, reviewRecords.size());
        assertEquals("PENDING", reviewRecords.get(0).getStatus());
    }

    @Test
    void executeTask_schedulesRetryWhenProcessingFailsBeforeMaxRetry() {
        var media = insertMedia(USER_ID, MediaStatus.UPLOADED.name(), "executor-retry.bin");
        media.setMediaType("BROKEN_TYPE");
        mediaMapper.updateById(media);
        MediaProcessTask task = insertMediaTask(media.getId(), USER_ID, TaskStatus.PENDING.name(), 0, 3);

        mediaTaskExecutor.executeTask(task.getId());

        var persistedTask = mediaProcessTaskMapper.selectById(task.getId());
        var persistedMedia = mediaMapper.selectById(media.getId());

        assertEquals(TaskStatus.RETRY_WAIT.name(), persistedTask.getStatus());
        assertEquals(1, persistedTask.getRetryCount());
        assertNotNull(persistedTask.getNextRunAt());
        assertTrue(persistedTask.getNextRunAt().isAfter(LocalDateTime.now().minusSeconds(5)));
        assertTrue(persistedTask.getErrorMessage().contains("BROKEN_TYPE"));
        assertEquals(MediaStatus.PROCESSING.name(), persistedMedia.getStatus());
    }

    @Test
    void executeTask_marksTaskAndMediaFailedWhenRetryExhausted() {
        var media = insertMedia(USER_ID, MediaStatus.UPLOADED.name(), "executor-failed.bin");
        media.setMediaType("BROKEN_TYPE");
        mediaMapper.updateById(media);
        MediaProcessTask task = insertMediaTask(media.getId(), USER_ID, TaskStatus.PENDING.name(), 2, 3);

        mediaTaskExecutor.executeTask(task.getId());

        var persistedTask = mediaProcessTaskMapper.selectById(task.getId());
        var persistedMedia = mediaMapper.selectById(media.getId());

        assertEquals(TaskStatus.FAILED.name(), persistedTask.getStatus());
        assertEquals(3, persistedTask.getRetryCount());
        assertNotNull(persistedTask.getFinishedAt());
        assertTrue(persistedTask.getErrorMessage().contains("BROKEN_TYPE"));
        assertEquals(MediaStatus.FAILED.name(), persistedMedia.getStatus());
        assertEquals(persistedTask.getErrorMessage(), persistedMedia.getErrorMessage());
    }

    @Test
    void executeTask_marksTaskFailedWhenMediaMissing() {
        MediaProcessTask task = insertMediaTask(999999L, USER_ID, TaskStatus.PENDING.name(), 0, 3);

        mediaTaskExecutor.executeTask(task.getId());

        var persistedTask = mediaProcessTaskMapper.selectById(task.getId());

        assertEquals(TaskStatus.FAILED.name(), persistedTask.getStatus());
        assertEquals("media not found", persistedTask.getErrorMessage());
        assertNotNull(persistedTask.getFinishedAt());
        assertNull(persistedTask.getStartedAt());
    }

    private MediaProcessTask insertMediaTask(Long mediaId, Long userId, String status, int retryCount, int maxRetry) {
        MediaProcessTask task = new MediaProcessTask();
        task.setMediaId(mediaId);
        task.setUserId(userId);
        task.setTaskType("MEDIA_PROCESS");
        task.setStatus(status);
        task.setRetryCount(retryCount);
        task.setMaxRetry(maxRetry);
        task.setNextRunAt(LocalDateTime.now());
        mediaProcessTaskMapper.insert(task);
        return task;
    }
}
