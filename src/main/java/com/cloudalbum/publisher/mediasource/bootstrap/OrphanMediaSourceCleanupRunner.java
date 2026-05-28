package com.cloudalbum.publisher.mediasource.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.mediasource.entity.MediaSource;
import com.cloudalbum.publisher.mediasource.mapper.MediaSourceMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanMediaSourceCleanupRunner implements ApplicationRunner {

    private static final Set<String> EXTERNAL_SOURCE_TYPES = Set.of("SMB", "FTP", "SFTP", "WEBDAV");

    private final MediaMapper mediaMapper;
    private final MediaSourceMapper mediaSourceMapper;
    private final MinioClient minioClient;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Media> externalMediaList = mediaMapper.selectList(new LambdaQueryWrapper<Media>()
                .in(Media::getSourceType, EXTERNAL_SOURCE_TYPES)
                .isNotNull(Media::getSourceId));
        if (externalMediaList.isEmpty()) {
            return;
        }

        Set<Long> sourceIds = externalMediaList.stream()
                .map(Media::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sourceIds.isEmpty()) {
            return;
        }

        Set<Long> existingSourceIds = mediaSourceMapper.selectList(new LambdaQueryWrapper<MediaSource>()
                        .in(MediaSource::getId, sourceIds))
                .stream()
                .map(MediaSource::getId)
                .collect(Collectors.toSet());

        List<Media> orphanMediaList = externalMediaList.stream()
                .filter(media -> !existingSourceIds.contains(media.getSourceId()))
                .toList();
        if (orphanMediaList.isEmpty()) {
            return;
        }

        orphanMediaList.forEach(this::deleteMediaObjects);
        mediaMapper.delete(new LambdaQueryWrapper<Media>()
                .in(Media::getId, orphanMediaList.stream().map(Media::getId).toList()));
        log.info("Cleaned orphan external media after deleted media sources, count={}", orphanMediaList.size());
    }

    private void deleteMediaObjects(Media media) {
        deleteObjectIfPresent(media.getBucketName(), media.getObjectKey());
        deleteObjectIfPresent(media.getBucketName(), media.getThumbnailKey());
    }

    private void deleteObjectIfPresent(String bucketName, String objectKey) {
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            log.warn("Delete orphan media object from MinIO failed, bucket={}, object={}", bucketName, objectKey, ex);
        }
    }
}
