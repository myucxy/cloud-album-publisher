package com.cloudalbum.publisher.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudalbum.publisher.common.constant.CacheConstants;
import com.cloudalbum.publisher.common.enums.*;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.media.content.MediaContentResolverRegistry;
import com.cloudalbum.publisher.media.content.MediaHttpWriter;
import com.cloudalbum.publisher.media.dto.*;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.entity.MediaProcessTask;
import com.cloudalbum.publisher.media.entity.UploadPart;
import com.cloudalbum.publisher.media.entity.UploadSession;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.media.mapper.MediaProcessTaskMapper;
import com.cloudalbum.publisher.media.mapper.UploadPartMapper;
import com.cloudalbum.publisher.media.mapper.UploadSessionMapper;
import com.cloudalbum.publisher.media.service.MediaService;
import com.cloudalbum.publisher.media.util.MediaTypeUtil;
import com.cloudalbum.publisher.review.entity.ReviewRecord;
import com.cloudalbum.publisher.review.mapper.ReviewRecordMapper;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private static final String SOURCE_TYPE_UPLOAD = "UPLOAD";
    private static final String SOURCE_NAME_UPLOAD = "上传";
    private static final String DEFAULT_FOLDER_PATH = "/上传";
    private static final String INGEST_MODE_UPLOADED = "UPLOADED";

    private final MediaMapper mediaMapper;
    private final UploadSessionMapper uploadSessionMapper;
    private final UploadPartMapper uploadPartMapper;
    private final MediaProcessTaskMapper mediaProcessTaskMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final MinioClient minioClient;
    private final MediaContentResolverRegistry mediaContentResolverRegistry;
    private final MediaHttpWriter mediaHttpWriter;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${media.upload.part-size:5242880}")
    private long partSize;

    @Value("${media.upload.session-expire-hours:24}")
    private long sessionExpireHours;

    @Value("${media.task.max-retry:3}")
    private int maxRetry;

    @Override
    public PageResult<MediaResponse> listMedia(Long userId,
                                               PageRequest pageRequest,
                                               String status,
                                               String mediaType,
                                               String sourceType,
                                               Long sourceId,
                                               String folderPath,
                                               String keyword) {
        LambdaQueryWrapper<Media> queryWrapper = buildMediaQuery(userId, status, mediaType, sourceType, sourceId, folderPath, keyword)
                .orderByDesc(Media::getCreatedAt);

        IPage<Media> page = mediaMapper.selectPage(new Page<>(pageRequest.getPage(), pageRequest.getSize()), queryWrapper);
        Map<Long, ReviewRecord> latestReviewMap = queryLatestReviewMap(
                page.getRecords().stream().map(Media::getId).toList());
        List<MediaResponse> list = page.getRecords().stream()
                .map(media -> toMediaResponse(media, latestReviewMap.get(media.getId())))
                .toList();
        return PageResult.of(page.getTotal(), pageRequest.getPage(), pageRequest.getSize(), list);
    }

    @Override
    public MediaLibraryGroupsResponse getMediaGroups(Long userId, String keyword) {
        List<Media> mediaList = mediaMapper.selectList(buildMediaQuery(userId, null, null, null, null, null, keyword)
                .orderByAsc(Media::getSourceType)
                .orderByAsc(Media::getSourceName)
                .orderByAsc(Media::getFolderPath)
                .orderByAsc(Media::getFileName));

        MediaLibraryGroupsResponse response = new MediaLibraryGroupsResponse();
        Map<String, MediaLibraryGroupsResponse.SourceGroup> sourceGroupMap = new LinkedHashMap<>();
        Map<String, MediaLibraryGroupsResponse.FacetCount> mediaTypeMap = new LinkedHashMap<>();

        for (Media media : mediaList) {
            String resolvedSourceType = defaultSourceType(media.getSourceType());
            String resolvedSourceName = defaultSourceName(media.getSourceName(), resolvedSourceType);
            String resolvedFolderPath = defaultFolderPath(media.getFolderPath());
            String sourceKey = resolvedSourceType + "#" + (media.getSourceId() == null ? "default" : media.getSourceId());
            MediaLibraryGroupsResponse.SourceGroup sourceGroup = sourceGroupMap.computeIfAbsent(sourceKey, key -> {
                MediaLibraryGroupsResponse.SourceGroup created = new MediaLibraryGroupsResponse.SourceGroup();
                created.setSourceType(resolvedSourceType);
                created.setSourceId(media.getSourceId());
                created.setSourceName(resolvedSourceName);
                return created;
            });
            sourceGroup.setMediaCount(sourceGroup.getMediaCount() + 1);
            addFolderGroup(sourceGroup, resolvedFolderPath);

            String resolvedMediaType = StringUtils.hasText(media.getMediaType()) ? media.getMediaType() : "OTHER";
            MediaLibraryGroupsResponse.FacetCount facetCount = mediaTypeMap.computeIfAbsent(resolvedMediaType, key -> {
                MediaLibraryGroupsResponse.FacetCount created = new MediaLibraryGroupsResponse.FacetCount();
                created.setValue(resolvedMediaType);
                created.setLabel(mediaTypeLabel(resolvedMediaType));
                return created;
            });
            facetCount.setCount(facetCount.getCount() + 1);
        }

        response.setSourceGroups(new ArrayList<>(sourceGroupMap.values()));
        response.setMediaTypeGroups(new ArrayList<>(mediaTypeMap.values()));
        return response;
    }

    @Override
    @Transactional
    public MediaUploadInitResponse initUpload(Long userId, MediaUploadInitRequest request) {
        try {
            ensureBucketReady();
            int totalParts = (int) Math.ceil((double) request.getFileSize() / partSize);
            String uploadId = UUID.randomUUID().toString().replace("-", "");
            String objectKey = buildObjectKey(userId, request.getFileName());

            UploadSession session = new UploadSession();
            session.setUploadId(uploadId);
            session.setUserId(userId);
            session.setFileName(request.getFileName());
            session.setContentType(request.getContentType());
            session.setFileSize(request.getFileSize());
            session.setTotalParts(Math.max(totalParts, 1));
            session.setUploadedParts(0);
            session.setObjectKey(objectKey);
            session.setStatus("UPLOADING");
            session.setExpiresAt(LocalDateTime.now().plusHours(sessionExpireHours));
            uploadSessionMapper.insert(session);

            redisTemplate.opsForValue().set(
                    CacheConstants.UPLOAD_PROGRESS_KEY + uploadId, 0, Duration.ofHours(sessionExpireHours));

            MediaUploadInitResponse response = new MediaUploadInitResponse();
            response.setUploadId(uploadId);
            response.setObjectKey(objectKey);
            response.setPartSize(partSize);
            response.setTotalParts(session.getTotalParts());
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Init upload failed", ex);
            throw new BusinessException(ResultCode.MEDIA_UPLOAD_INIT_FAILED, ex.getMessage());
        }
    }

    @Override
    @Transactional
    public MediaUploadPartResponse uploadPart(Long userId, String uploadId, Integer partNumber, MultipartFile file) {
        if (partNumber == null || partNumber < 1 || file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片参数错误");
        }
        UploadSession session = getUploadSession(uploadId, userId);
        checkUploadSessionStatus(session);
        if (partNumber > session.getTotalParts()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片序号超出总分片数");
        }

        String partObjectKey = buildPartObjectKey(uploadId, partNumber);
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(partObjectKey)
                            .contentType("application/octet-stream")
                            .stream(is, file.getSize(), -1)
                            .build()
            );
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "上传分片失败: " + ex.getMessage());
        }

        UploadPart uploadPart = uploadPartMapper.selectOne(new LambdaQueryWrapper<UploadPart>()
                .eq(UploadPart::getUploadId, uploadId)
                .eq(UploadPart::getPartNumber, partNumber)
                .last("limit 1"));

        if (uploadPart == null) {
            uploadPart = new UploadPart();
            uploadPart.setUploadId(uploadId);
            uploadPart.setPartNumber(partNumber);
            uploadPart.setPartObjectKey(partObjectKey);
            uploadPart.setEtag(String.valueOf(file.getSize()));
            uploadPart.setPartSize(file.getSize());
            uploadPartMapper.insert(uploadPart);
        } else {
            uploadPart.setPartObjectKey(partObjectKey);
            uploadPart.setEtag(String.valueOf(file.getSize()));
            uploadPart.setPartSize(file.getSize());
            uploadPartMapper.updateById(uploadPart);
        }

        int uploadedParts = Math.toIntExact(uploadPartMapper.selectCount(
                new LambdaQueryWrapper<UploadPart>().eq(UploadPart::getUploadId, uploadId)));

        session.setUploadedParts(uploadedParts);
        session.setStatus("UPLOADING");
        uploadSessionMapper.updateById(session);

        int progress = (int) ((uploadedParts * 100.0) / Math.max(session.getTotalParts(), 1));
        redisTemplate.opsForValue().set(
                CacheConstants.UPLOAD_PROGRESS_KEY + uploadId, progress, Duration.ofHours(sessionExpireHours));

        MediaUploadPartResponse response = new MediaUploadPartResponse();
        response.setUploadId(uploadId);
        response.setPartNumber(partNumber);
        response.setEtag(uploadPart.getEtag());
        response.setUploadedParts(uploadedParts);
        response.setTotalParts(session.getTotalParts());
        return response;
    }

    @Override
    @Transactional
    public MediaResponse completeUpload(Long userId, String uploadId, MediaUploadCompleteRequest request) {
        UploadSession session = getUploadSession(uploadId, userId);
        checkUploadSessionStatus(session);

        List<UploadPart> parts = uploadPartMapper.selectList(new LambdaQueryWrapper<UploadPart>()
                .eq(UploadPart::getUploadId, uploadId)
                .orderByAsc(UploadPart::getPartNumber));

        if (parts.size() < session.getTotalParts()) {
            throw new BusinessException(ResultCode.UPLOAD_PART_MISSING, "分片未全部上传");
        }

        if (request != null && !CollectionUtils.isEmpty(request.getParts())) {
            Map<Integer, String> partMap = request.getParts().stream()
                    .collect(Collectors.toMap(UploadPartRequestItem::getPartNumber, UploadPartRequestItem::getEtag, (a, b) -> b));
            for (UploadPart part : parts) {
                String etag = partMap.get(part.getPartNumber());
                if (!StringUtils.hasText(etag)) {
                    throw new BusinessException(ResultCode.UPLOAD_PART_MISSING, "请求缺少分片信息: " + part.getPartNumber());
                }
            }
        }

        try {
            List<ComposeSource> composeSources = parts.stream()
                    .map(p -> ComposeSource.builder().bucket(bucket).object(p.getPartObjectKey()).build())
                    .toList();

            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucket)
                            .object(session.getObjectKey())
                            .sources(composeSources)
                            .build()
            );
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "合并分片失败: " + ex.getMessage());
        }

        Media media = new Media();
        media.setUserId(userId);
        media.setFileName(session.getFileName());
        media.setContentType(session.getContentType());
        media.setMediaType(MediaTypeUtil.detect(session.getContentType(), session.getFileName()).name());
        media.setFileSize(session.getFileSize());
        media.setSourceType(SOURCE_TYPE_UPLOAD);
        media.setSourceName(SOURCE_NAME_UPLOAD);
        media.setFolderPath(DEFAULT_FOLDER_PATH);
        media.setOriginUri(session.getObjectKey());
        media.setIngestMode(INGEST_MODE_UPLOADED);
        media.setBucketName(bucket);
        media.setObjectKey(session.getObjectKey());
        media.setStatus(MediaStatus.UPLOADED.name());
        mediaMapper.insert(media);

        createProcessTaskIfAbsent(media.getId(), userId);

        session.setStatus("COMPLETED");
        uploadSessionMapper.updateById(session);
        redisTemplate.delete(CacheConstants.UPLOAD_PROGRESS_KEY + uploadId);

        clearPartObjects(parts);
        return toMediaResponse(media, null);
    }

    @Override
    public MediaUploadStatusResponse getUploadStatus(Long userId, String uploadId) {
        UploadSession session = getUploadSession(uploadId, userId);
        List<Integer> uploaded = uploadPartMapper.selectList(
                        new LambdaQueryWrapper<UploadPart>()
                                .select(UploadPart::getPartNumber)
                                .eq(UploadPart::getUploadId, uploadId)
                                .orderByAsc(UploadPart::getPartNumber))
                .stream()
                .map(UploadPart::getPartNumber)
                .toList();

        Set<Integer> uploadedSet = new HashSet<>(uploaded);
        List<Integer> missing = IntStream.rangeClosed(1, session.getTotalParts())
                .filter(i -> !uploadedSet.contains(i))
                .boxed()
                .toList();

        int progress = (int) ((uploaded.size() * 100.0) / Math.max(session.getTotalParts(), 1));
        MediaUploadStatusResponse response = new MediaUploadStatusResponse();
        response.setUploadId(uploadId);
        response.setStatus(session.getStatus());
        response.setUploadedParts(uploaded.size());
        response.setTotalParts(session.getTotalParts());
        response.setProgress(progress);
        response.setMissingParts(missing);
        return response;
    }

    @Override
    public MediaResponse getMedia(Long mediaId, Long userId) {
        Media media = getOwnedMedia(mediaId, userId);
        return toMediaResponse(media, queryLatestReviewMap(List.of(mediaId)).get(mediaId));
    }

    @Override
    @Transactional
    public void deleteMedia(Long mediaId, Long userId) {
        Media media = getOwnedMedia(mediaId, userId);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(media.getBucketName())
                    .object(media.getObjectKey())
                    .build());
            if (StringUtils.hasText(media.getThumbnailKey())) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(media.getBucketName())
                        .object(media.getThumbnailKey())
                        .build());
            }
        } catch (Exception ex) {
            log.warn("Delete object from MinIO failed, mediaId={}", mediaId, ex);
        }
        mediaMapper.deleteById(mediaId);
    }

    @Override
    @Transactional
    public void triggerProcess(Long mediaId, Long userId) {
        Media media = getOwnedMedia(mediaId, userId);
        if (MediaStatus.PROCESSING.name().equals(media.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "媒体处理中，请稍后");
        }
        media.setStatus(MediaStatus.UPLOADED.name());
        media.setErrorMessage(null);
        mediaMapper.updateById(media);
        createProcessTaskIfAbsent(mediaId, userId);
    }

    @Override
    public MediaStatusResponse getMediaStatus(Long mediaId, Long userId) {
        Media media = getOwnedMedia(mediaId, userId);
        MediaProcessTask task = mediaProcessTaskMapper.selectOne(new LambdaQueryWrapper<MediaProcessTask>()
                .eq(MediaProcessTask::getMediaId, mediaId)
                .orderByDesc(MediaProcessTask::getId)
                .last("limit 1"));
        MediaStatusResponse response = new MediaStatusResponse();
        response.setMediaId(mediaId);
        response.setMediaStatus(media.getStatus());
        response.setTaskStatus(task == null ? null : task.getStatus());
        response.setErrorMessage(media.getErrorMessage());
        response.setUpdatedAt(media.getUpdatedAt());
        return response;
    }

    @Override
    public void writeMediaContent(Long mediaId, Long userId, boolean thumbnail, HttpServletRequest request, HttpServletResponse response) {
        Media media = getOwnedMedia(mediaId, userId);
        mediaHttpWriter.write(
                mediaContentResolverRegistry.resolve(media, thumbnail),
                request,
                response,
                "读取媒体内容失败");
    }


    private void createProcessTaskIfAbsent(Long mediaId, Long userId) {
        Long count = mediaProcessTaskMapper.selectCount(new LambdaQueryWrapper<MediaProcessTask>()
                .eq(MediaProcessTask::getMediaId, mediaId)
                .and(w -> w.eq(MediaProcessTask::getStatus, TaskStatus.PENDING.name())
                        .or().eq(MediaProcessTask::getStatus, TaskStatus.RUNNING.name())
                        .or().eq(MediaProcessTask::getStatus, TaskStatus.RETRY_WAIT.name())));
        if (count != null && count > 0) {
            return;
        }
        MediaProcessTask task = new MediaProcessTask();
        task.setMediaId(mediaId);
        task.setUserId(userId);
        task.setTaskType(TaskType.MEDIA_PROCESS.name());
        task.setStatus(TaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setMaxRetry(maxRetry);
        task.setNextRunAt(LocalDateTime.now());
        mediaProcessTaskMapper.insert(task);
    }

    private UploadSession getUploadSession(String uploadId, Long userId) {
        UploadSession session = uploadSessionMapper.selectById(uploadId);
        if (session == null) {
            throw new BusinessException(ResultCode.UPLOAD_SESSION_NOT_FOUND);
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(ResultCode.MEDIA_ACCESS_DENIED);
        }
        return session;
    }

    private void checkUploadSessionStatus(UploadSession session) {
        if ("COMPLETED".equals(session.getStatus())) {
            throw new BusinessException(ResultCode.UPLOAD_ALREADY_COMPLETED);
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传会话已过期");
        }
    }

    private Media getOwnedMedia(Long mediaId, Long userId) {
        Media media = mediaMapper.selectById(mediaId);
        if (media == null) {
            throw new BusinessException(ResultCode.MEDIA_NOT_FOUND);
        }
        if (!Objects.equals(media.getUserId(), userId)) {
            throw new BusinessException(ResultCode.MEDIA_ACCESS_DENIED);
        }
        return normalizeMedia(media);
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

    private MediaResponse toMediaResponse(Media media, ReviewRecord latestReview) {
        Media normalized = normalizeMedia(media);
        MediaResponse response = new MediaResponse();
        response.setId(normalized.getId());
        response.setUserId(normalized.getUserId());
        response.setFileName(normalized.getFileName());
        response.setContentType(normalized.getContentType());
        response.setMediaType(normalized.getMediaType());
        response.setFileSize(normalized.getFileSize());
        response.setSourceType(normalized.getSourceType());
        response.setSourceId(normalized.getSourceId());
        response.setSourceName(normalized.getSourceName());
        response.setFolderPath(normalized.getFolderPath());
        response.setOriginUri(normalized.getOriginUri());
        response.setIngestMode(normalized.getIngestMode());
        response.setUrl(buildContentUrl(normalized.getId()));
        response.setThumbnailUrl(buildThumbnailUrl(normalized.getId(), normalized.getThumbnailKey()));
        response.setDurationSec(normalized.getDurationSec());
        response.setWidth(normalized.getWidth());
        response.setHeight(normalized.getHeight());
        response.setStatus(normalized.getStatus());
        response.setReviewStatus(latestReview == null ? null : latestReview.getStatus());
        response.setReviewRejectReason(latestReview == null ? null : latestReview.getRejectReason());
        response.setReviewedAt(latestReview == null ? null : latestReview.getReviewedAt());
        response.setErrorMessage(normalized.getErrorMessage());
        response.setCreatedAt(normalized.getCreatedAt());
        response.setUpdatedAt(normalized.getUpdatedAt());
        return response;
    }

    private LambdaQueryWrapper<Media> buildMediaQuery(Long userId,
                                                      String status,
                                                      String mediaType,
                                                      String sourceType,
                                                      Long sourceId,
                                                      String folderPath,
                                                      String keyword) {
        LambdaQueryWrapper<Media> queryWrapper = new LambdaQueryWrapper<Media>()
                .eq(Media::getUserId, userId);

        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Media::getStatus, status);
        }
        if (StringUtils.hasText(mediaType)) {
            queryWrapper.eq(Media::getMediaType, mediaType);
        }
        if (StringUtils.hasText(sourceType)) {
            queryWrapper.eq(Media::getSourceType, sourceType);
        }
        if (sourceId != null) {
            queryWrapper.eq(Media::getSourceId, sourceId);
        }
        if (StringUtils.hasText(folderPath)) {
            queryWrapper.eq(Media::getFolderPath, folderPath);
        }
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Media::getFileName, keyword)
                    .or().like(Media::getSourceName, keyword)
                    .or().like(Media::getFolderPath, keyword)
                    .or().like(Media::getOriginUri, keyword));
        }

        return queryWrapper;
    }

    private void addFolderGroup(MediaLibraryGroupsResponse.SourceGroup sourceGroup, String folderPath) {
        MediaLibraryGroupsResponse.FolderGroup folderGroup = sourceGroup.getFolders().stream()
                .filter(item -> Objects.equals(item.getFolderPath(), folderPath))
                .findFirst()
                .orElseGet(() -> {
                    MediaLibraryGroupsResponse.FolderGroup created = new MediaLibraryGroupsResponse.FolderGroup();
                    created.setFolderPath(folderPath);
                    created.setTitle(folderTitle(folderPath));
                    sourceGroup.getFolders().add(created);
                    return created;
                });
        folderGroup.setMediaCount(folderGroup.getMediaCount() + 1);
    }

    private Media normalizeMedia(Media media) {
        if (!StringUtils.hasText(media.getSourceType())) {
            media.setSourceType(SOURCE_TYPE_UPLOAD);
        }
        if (!StringUtils.hasText(media.getSourceName())) {
            media.setSourceName(defaultSourceName(media.getSourceName(), media.getSourceType()));
        }
        if (!StringUtils.hasText(media.getFolderPath())) {
            media.setFolderPath(DEFAULT_FOLDER_PATH);
        }
        if (!StringUtils.hasText(media.getIngestMode())) {
            media.setIngestMode(INGEST_MODE_UPLOADED);
        }
        if (!StringUtils.hasText(media.getOriginUri()) && StringUtils.hasText(media.getObjectKey())) {
            media.setOriginUri(media.getObjectKey());
        }
        return media;
    }

    private String defaultSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? sourceType : SOURCE_TYPE_UPLOAD;
    }

    private String defaultSourceName(String sourceName, String sourceType) {
        if (StringUtils.hasText(sourceName)) {
            return sourceName;
        }
        return SOURCE_TYPE_UPLOAD.equals(sourceType) ? SOURCE_NAME_UPLOAD : sourceType;
    }

    private String defaultFolderPath(String folderPath) {
        return StringUtils.hasText(folderPath) ? folderPath : DEFAULT_FOLDER_PATH;
    }

    private String folderTitle(String folderPath) {
        if (!StringUtils.hasText(folderPath) || "/".equals(folderPath)) {
            return "根目录";
        }
        int idx = folderPath.lastIndexOf('/');
        if (idx >= 0 && idx < folderPath.length() - 1) {
            return folderPath.substring(idx + 1);
        }
        return folderPath;
    }

    private String mediaTypeLabel(String mediaType) {
        return switch (mediaType) {
            case "IMAGE" -> "图片";
            case "VIDEO" -> "视频";
            case "AUDIO" -> "音频";
            default -> mediaType;
        };
    }

    private void ensureBucketReady() throws Exception {
        boolean exists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(bucket).build()
        );
        if (!exists) {
            minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String buildObjectKey(Long userId, String fileName) {
        String safeName = fileName == null ? "unknown.bin" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "media/" + userId + "/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + safeName;
    }

    private String buildPartObjectKey(String uploadId, Integer partNumber) {
        return "upload-parts/" + uploadId + "/part-" + partNumber;
    }

    private void clearPartObjects(List<UploadPart> parts) {
        for (UploadPart part : parts) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(part.getPartObjectKey())
                        .build());
            } catch (Exception ex) {
                log.warn("Clear upload part object failed, uploadId={}, part={}", part.getUploadId(), part.getPartNumber(), ex);
            }
        }
    }

    private String buildContentUrl(Long mediaId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/media/{id}/content")
                .buildAndExpand(mediaId)
                .toUriString();
    }

    private String buildThumbnailUrl(Long mediaId, String thumbnailKey) {
        if (!StringUtils.hasText(thumbnailKey)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/media/{id}/thumbnail")
                .buildAndExpand(mediaId)
                .toUriString();
    }
}
