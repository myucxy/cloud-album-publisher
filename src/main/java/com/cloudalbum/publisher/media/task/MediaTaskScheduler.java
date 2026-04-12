package com.cloudalbum.publisher.media.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.common.constant.CacheConstants;
import com.cloudalbum.publisher.common.enums.TaskStatus;
import com.cloudalbum.publisher.common.util.RedisLockUtil;
import com.cloudalbum.publisher.media.entity.MediaProcessTask;
import com.cloudalbum.publisher.media.mapper.MediaProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaTaskScheduler {

    private final MediaProcessTaskMapper mediaProcessTaskMapper;
    private final MediaTaskExecutor mediaTaskExecutor;
    private final RedisLockUtil redisLockUtil;

    @Value("${media.task.poll-batch-size:10}")
    private int pollBatchSize;

    @Value("${media.task.lock-seconds:30}")
    private int lockSeconds;

    @Scheduled(fixedDelayString = "${media.task.poll-interval-ms:5000}")
    public void pollAndExecute() {
        LocalDateTime now = LocalDateTime.now();
        List<MediaProcessTask> tasks = mediaProcessTaskMapper.selectList(new LambdaQueryWrapper<MediaProcessTask>()
                .and(w -> w.eq(MediaProcessTask::getStatus, TaskStatus.PENDING.name())
                        .or()
                        .eq(MediaProcessTask::getStatus, TaskStatus.RETRY_WAIT.name()))
                .le(MediaProcessTask::getNextRunAt, now)
                .orderByAsc(MediaProcessTask::getNextRunAt)
                .last("limit " + pollBatchSize));

        for (MediaProcessTask task : tasks) {
            String lockKey = CacheConstants.MEDIA_TASK_LOCK_KEY + task.getId();
            String token = redisLockUtil.tryLock(lockKey, Duration.ofSeconds(lockSeconds));
            if (token == null) {
                continue;
            }
            try {
                mediaTaskExecutor.executeTask(task.getId());
            } catch (Exception ex) {
                log.error("Run media task failed, taskId={}", task.getId(), ex);
            } finally {
                redisLockUtil.unlock(lockKey, token);
            }
        }
    }
}
