package com.cloudalbum.publisher.mediasource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.common.enums.MediaStatus;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.enums.TaskStatus;
import com.cloudalbum.publisher.common.enums.TaskType;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.common.security.CredentialCryptoService;
import com.cloudalbum.publisher.media.content.MediaHttpWriter;
import com.cloudalbum.publisher.media.content.ObjectStorageMediaContentResolver;
import com.cloudalbum.publisher.media.dto.MediaResponse;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.entity.MediaProcessTask;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.media.mapper.MediaProcessTaskMapper;
import com.cloudalbum.publisher.media.util.MediaTypeUtil;
import com.cloudalbum.publisher.mediasource.dto.ConnectionTestResponse;
import com.cloudalbum.publisher.mediasource.dto.ExternalMediaItemResponse;
import com.cloudalbum.publisher.mediasource.dto.ExternalMediaScanSummary;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseItemResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceCreateRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceImportRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceUpdateRequest;
import com.cloudalbum.publisher.mediasource.entity.MediaSource;
import com.cloudalbum.publisher.mediasource.mapper.MediaSourceMapper;
import com.cloudalbum.publisher.mediasource.service.MediaSourceService;
import com.cloudalbum.publisher.mediasource.smb.MediaSourceFileClient;
import com.cloudalbum.publisher.mediasource.type.MediaSourceTypeHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaSourceServiceImpl implements MediaSourceService {

    private static final Set<String> EXTERNAL_SOURCE_TYPES = Set.of("SMB", "FTP", "SFTP", "WEBDAV");
    private static final String INGEST_MODE_CACHED = "CACHED";
    private static final String INGEST_MODE_LINKED = "LINKED";
    private static final String EXTERNAL_CACHE_PREFIX = "external-cache/";
    private static final int IMAGE_THUMB_MAX_EDGE = 480;
    private static final int MAX_RECURSIVE_SCAN_DIRECTORIES = 500;
    private static final int MAX_RECURSIVE_SCAN_FILES = 5000;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final MediaSourceMapper mediaSourceMapper;
    private final MediaMapper mediaMapper;
    private final MediaProcessTaskMapper mediaProcessTaskMapper;
    private final CredentialCryptoService credentialCryptoService;
    private final List<MediaSourceFileClient> mediaSourceFileClients;
    private final List<MediaSourceTypeHandler> typeHandlers;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final MediaHttpWriter mediaHttpWriter;
    private final ObjectStorageMediaContentResolver objectStorageMediaContentResolver;
    private final Tika tika = new Tika();

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${media.task.max-retry:3}")
    private int maxRetry;

    @Override
    public List<MediaSourceResponse> listMediaSources(Long userId) {
        return mediaSourceMapper.selectList(new LambdaQueryWrapper<MediaSource>()
                        .eq(MediaSource::getUserId, userId)
                        .orderByDesc(MediaSource::getUpdatedAt)
                        .orderByDesc(MediaSource::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public MediaSourceResponse createMediaSource(Long userId, MediaSourceCreateRequest request) {
        String sourceType = normalizeSourceType(request.getSourceType());
        ensureSupportedSourceType(sourceType);
        ensureImplementedSourceType(sourceType);

        MediaSource mediaSource = new MediaSource();
        mediaSource.setUserId(userId);
        mediaSource.setSourceType(sourceType);
        mediaSource.setName(requireText(request.getName(), "媒体源名称不能为空"));
        mediaSource.setEnabled(resolveEnabled(request.getEnabled()));

        Map<String, Object> config = normalizeConfig(sourceType, request.getConfig());
        Map<String, Object> credentials = normalizeCredentials(sourceType, request.getCredentials(), true);
        applySourceDefinition(mediaSource, config, credentials);

        String browseRoot = browseRootPath(mediaSource, false);
        String boundPath = resolveRequestedPath(mediaSource, request.getBoundPath(), browseRoot, false);
        mediaSource.setBoundPath(boundPath);
        mediaSource.setBoundPathName(resolveBoundPathName(request.getBoundPathName(), boundPath));

        mediaSourceMapper.insert(mediaSource);
        return toResponse(mediaSource);
    }

    @Override
    @Transactional
    public MediaSourceResponse updateMediaSource(Long mediaSourceId, Long userId, MediaSourceUpdateRequest request) {
        MediaSource mediaSource = getOwnedMediaSource(mediaSourceId, userId);
        String sourceType = normalizeSourceType(mediaSource.getSourceType());
        ensureImplementedSourceType(sourceType);

        if (StringUtils.hasText(request.getName())) {
            mediaSource.setName(request.getName().trim());
        }

        Map<String, Object> mergedConfig = new LinkedHashMap<>(readConfig(mediaSource));
        if (!CollectionUtils.isEmpty(request.getConfig())) {
            mergedConfig.putAll(request.getConfig());
        }
        mergedConfig = normalizeConfig(sourceType, mergedConfig);

        Map<String, Object> mergedCredentials = new LinkedHashMap<>(readCredentials(mediaSource));
        Map<String, Object> requestCredentials = request.getCredentials();
        if (!CollectionUtils.isEmpty(requestCredentials)) {
            for (Map.Entry<String, Object> entry : requestCredentials.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (!"password".equals(key) || StringUtils.hasText(asText(value))) {
                    mergedCredentials.put(key, value);
                }
            }
        }
        mergedCredentials = normalizeCredentials(sourceType, mergedCredentials, false);

        applySourceDefinition(mediaSource, mergedConfig, mergedCredentials);

        String targetBoundPath = request.getBoundPath() != null ? request.getBoundPath() : mediaSource.getBoundPath();
        if (!StringUtils.hasText(targetBoundPath)) {
            targetBoundPath = browseRootPath(mediaSource, false);
        }
        String normalizedBoundPath = resolveRequestedPath(mediaSource, targetBoundPath, browseRootPath(mediaSource, false), false);
        mediaSource.setBoundPath(normalizedBoundPath);
        if (request.getBoundPathName() != null) {
            mediaSource.setBoundPathName(resolveBoundPathName(request.getBoundPathName(), normalizedBoundPath));
        } else if (request.getBoundPath() != null || !StringUtils.hasText(mediaSource.getBoundPathName())) {
            mediaSource.setBoundPathName(resolveBoundPathName(null, normalizedBoundPath));
        }

        if (request.getEnabled() != null) {
            mediaSource.setEnabled(resolveEnabled(request.getEnabled()));
        }

        mediaSourceMapper.updateById(mediaSource);
        return toResponse(mediaSource);
    }

    @Override
    @Transactional
    public void deleteMediaSource(Long mediaSourceId, Long userId) {
        MediaSource mediaSource = getOwnedMediaSource(mediaSourceId, userId);
        List<Media> importedMediaList = mediaMapper.selectList(new LambdaQueryWrapper<Media>()
                .eq(Media::getUserId, userId)
                .eq(Media::getSourceId, mediaSource.getId()));
        importedMediaList.forEach(this::deleteMediaObjects);
        mediaMapper.delete(new LambdaQueryWrapper<Media>()
                .eq(Media::getUserId, userId)
                .eq(Media::getSourceId, mediaSource.getId()));
        mediaSourceMapper.deleteById(mediaSource.getId());
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
        } catch (Exception ignored) {
        }
    }

    @Override
    public MediaSourceBrowseResponse browse(Long mediaSourceId, Long userId, String path) {
        MediaSource mediaSource = getEnabledMediaSource(mediaSourceId, userId);
        String browseRoot = browseRootPath(mediaSource, true);
        String requestedPath = resolveRequestedPath(mediaSource, path, browseRoot, true);
        return buildBrowseResponse(mediaSource, requestedPath, browseRoot);
    }

    @Override
    public MediaSourceBrowseResponse browse(Long userId, MediaSourceBrowseRequest request) {
        if (request != null && request.getSourceId() != null) {
            return browse(request.getSourceId(), userId, request.getPath());
        }
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "缺少媒体源浏览参数");
        }
        String sourceType = normalizeSourceType(request.getSourceType());
        ensureSupportedSourceType(sourceType);
        ensureImplementedSourceType(sourceType);

        MediaSource mediaSource = new MediaSource();
        mediaSource.setSourceType(sourceType);
        mediaSource.setName(requireText(request.getName(), "媒体源名称不能为空"));
        applySourceDefinition(
                mediaSource,
                normalizeConfig(sourceType, request.getConfig()),
                normalizeCredentials(sourceType, request.getCredentials(), true)
        );

        String browseRoot = browseRootPath(mediaSource, false);
        String requestedPath = resolveRequestedPath(mediaSource, request.getPath(), browseRoot, false);
        return buildBrowseResponse(mediaSource, requestedPath, browseRoot);
    }

    @Override
    public PageResult<ExternalMediaItemResponse> listExternalMedia(Long mediaSourceId,
                                                                   Long userId,
                                                                   PageRequest pageRequest,
                                                                   String path,
                                                                   String folderPath,
                                                                   String mediaType,
                                                                   String status,
                                                                   Boolean coverOnly,
                                                                   String keyword) {
        MediaSource mediaSource = getEnabledMediaSource(mediaSourceId, userId);
        String browseRoot = browseRootPath(mediaSource, true);
        String startPath = resolveRequestedPath(mediaSource, path, browseRoot, true);
        List<ExternalMediaItemResponse> items = scanExternalMediaItems(mediaSource, startPath).stream()
                .filter(item -> !StringUtils.hasText(folderPath) || Objects.equals(item.getFolderPath(), folderPath))
                .filter(item -> !StringUtils.hasText(mediaType) || Objects.equals(item.getMediaType(), mediaType))
                .filter(item -> !Boolean.TRUE.equals(coverOnly) || "IMAGE".equals(item.getMediaType()) || "VIDEO".equals(item.getMediaType()))
                .filter(item -> !StringUtils.hasText(status) || Objects.equals(item.getStatus(), status))
                .filter(item -> !StringUtils.hasText(keyword)
                        || containsIgnoreCase(item.getFileName(), keyword)
                        || containsIgnoreCase(item.getFolderPath(), keyword)
                        || containsIgnoreCase(item.getSourceName(), keyword))
                .sorted(Comparator.comparing(ExternalMediaItemResponse::getFolderPath, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ExternalMediaItemResponse::getFileName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int fromIndex = Math.min((int) pageRequest.getOffset(), items.size());
        int toIndex = Math.min(fromIndex + pageRequest.getSize(), items.size());
        return PageResult.of(items.size(), pageRequest.getPage(), pageRequest.getSize(), items.subList(fromIndex, toIndex));
    }

    @Override
    public List<ExternalMediaScanSummary> scanExternalMediaSummaries(Long userId, String keyword) {
        return mediaSourceMapper.selectList(new LambdaQueryWrapper<MediaSource>()
                        .eq(MediaSource::getUserId, userId)
                        .eq(MediaSource::getEnabled, true)
                        .orderByAsc(MediaSource::getSourceType)
                        .orderByAsc(MediaSource::getName)
                        .orderByAsc(MediaSource::getId))
                .stream()
                .filter(mediaSource -> EXTERNAL_SOURCE_TYPES.contains(normalizeSourceType(mediaSource.getSourceType())))
                .map(mediaSource -> buildExternalMediaSummary(mediaSource, keyword))
                .toList();
    }

    @Override
    public ConnectionTestResponse testConnection(Long mediaSourceId, Long userId) {
        MediaSource mediaSource = getOwnedMediaSource(mediaSourceId, userId);
        String sourceType = normalizeSourceType(mediaSource.getSourceType());
        ensureImplementedSourceType(sourceType);

        long startTime = System.currentTimeMillis();
        try {
            MediaSourceFileClient client = getRequiredFileClient(sourceType);
            MediaSourceFileClient.MediaSourceConnection connection = buildConnection(mediaSource);
            String browseRoot = browseRootPath(mediaSource, false);
            client.list(connection, browseRoot);
            long latency = System.currentTimeMillis() - startTime;
            return ConnectionTestResponse.builder()
                    .success(true)
                    .message("连接成功")
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            return ConnectionTestResponse.builder()
                    .success(false)
                    .message("连接失败: " + e.getMessage())
                    .latencyMs(latency)
                    .build();
        }
    }

    @Override
    public void writeMediaContent(Long mediaSourceId,
                                  Long userId,
                                  String path,
                                  boolean thumbnail,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        MediaSource mediaSource = getEnabledMediaSource(mediaSourceId, userId);
        String normalizedPath = resolveRequestedPath(mediaSource, path, browseRootPath(mediaSource, true), true);
        MediaSourceFileClient.Entry entry = getFileEntry(mediaSource, normalizedPath);
        if (entry.isDirectory()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "目录不支持直接预览");
        }

        String contentType = detectContentType(entry.getName());
        String mediaType = MediaTypeUtil.detect(contentType, entry.getName()).name();
        if (thumbnail && !"IMAGE".equals(mediaType)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前文件暂无缩略图");
        }

        String objectKey = ensureExternalCacheObject(mediaSource, entry, normalizedPath, thumbnail, contentType, mediaType);
        mediaHttpWriter.write(
                objectStorageMediaContentResolver.resolveObject(
                        bucket,
                        objectKey,
                        thumbnail ? "image/jpeg" : contentType,
                        thumbnail ? "缩略图不存在" : "媒体内容不存在"),
                request,
                response,
                thumbnail ? "读取外部媒体缩略图失败" : "读取外部媒体内容失败");
    }

    @Override
    @Transactional
    public List<MediaResponse> importMedia(Long mediaSourceId, Long userId, MediaSourceImportRequest request) {
        MediaSource mediaSource = getEnabledMediaSource(mediaSourceId, userId);
        ensureImplementedSourceType(normalizeSourceType(mediaSource.getSourceType()));
        ensureBucketReady();

        List<String> targetPaths = resolveImportPaths(mediaSource, request);
        if (CollectionUtils.isEmpty(targetPaths)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未选择可导入的文件");
        }

        return targetPaths.stream()
                .map(path -> importSingleFile(mediaSource, userId, path))
                .toList();
    }

    private MediaResponse importSingleFile(MediaSource mediaSource, Long userId, String sourcePath) {
        String normalizedPath = resolveRequestedPath(mediaSource, sourcePath, browseRootPath(mediaSource, true), true);
        String fileName = fileName(normalizedPath);
        String contentType = detectContentType(fileName);
        String mediaType = MediaTypeUtil.detect(contentType, fileName).name();
        if ("OTHER".equals(mediaType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持导入图片、视频或音频文件");
        }

        String objectKey = buildObjectKey(userId, fileName);
        long fileSize;
        try (InputStream inputStream = getRequiredFileClient(normalizeSourceType(mediaSource.getSourceType()))
                .open(buildConnection(mediaSource), normalizedPath)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(inputStream, -1, 10 * 1024 * 1024)
                    .build());
            fileSize = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()).size();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "导入媒体失败: " + ex.getMessage());
        }

        Media media = mediaMapper.selectOne(new LambdaQueryWrapper<Media>()
                .eq(Media::getUserId, userId)
                .eq(Media::getSourceId, mediaSource.getId())
                .eq(Media::getOriginUri, normalizedPath)
                .last("limit 1"));

        if (media == null) {
            media = new Media();
            media.setUserId(userId);
        }

        media.setFileName(fileName);
        media.setContentType(contentType);
        media.setMediaType(mediaType);
        media.setFileSize(fileSize);
        media.setSourceType(normalizeSourceType(mediaSource.getSourceType()));
        media.setSourceId(mediaSource.getId());
        media.setSourceName(mediaSource.getName());
        media.setFolderPath(resolveFolderPath(mediaSource, normalizedPath));
        media.setOriginUri(normalizedPath);
        media.setIngestMode(INGEST_MODE_CACHED);
        media.setBucketName(bucket);
        media.setObjectKey(objectKey);
        media.setThumbnailKey(null);
        media.setDurationSec(null);
        media.setWidth(null);
        media.setHeight(null);
        media.setStatus(MediaStatus.UPLOADED.name());
        media.setErrorMessage(null);

        if (media.getId() == null) {
            mediaMapper.insert(media);
        } else {
            mediaMapper.updateById(media);
        }

        createProcessTaskIfAbsent(media.getId(), userId);
        return toMediaResponse(media);
    }

    private List<String> resolveImportPaths(MediaSource mediaSource, MediaSourceImportRequest request) {
        String browseRoot = browseRootPath(mediaSource, true);
        if (request != null && !CollectionUtils.isEmpty(request.getPaths())) {
            return request.getPaths().stream()
                    .filter(StringUtils::hasText)
                    .map(path -> resolveRequestedPath(mediaSource, path, browseRoot, true))
                    .distinct()
                    .toList();
        }

        String directoryPath = request != null ? request.getDirectoryPath() : null;
        String targetDirectory = resolveRequestedPath(mediaSource, directoryPath, browseRoot, true);
        return resolveDirectoryImportPaths(mediaSource, targetDirectory, browseRoot);
    }

    private List<String> resolveDirectoryImportPaths(MediaSource mediaSource, String targetDirectory, String browseRoot) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (shouldScanSubdirectories(mediaSource)) {
            collectImportableFiles(mediaSource, targetDirectory, browseRoot, paths);
        } else {
            listEntries(mediaSource, targetDirectory).stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(MediaSourceFileClient.Entry::getPath)
                    .map(path -> resolveRequestedPath(mediaSource, path, browseRoot, true))
                    .filter(this::isImportableFile)
                    .forEach(paths::add);
        }
        return List.copyOf(paths);
    }

    private void collectImportableFiles(MediaSource mediaSource, String directoryPath, String browseRoot, LinkedHashSet<String> paths) {
        for (MediaSourceFileClient.Entry entry : listEntries(mediaSource, directoryPath)) {
            String resolvedPath = resolveRequestedPath(mediaSource, entry.getPath(), browseRoot, true);
            if (entry.isDirectory()) {
                collectImportableFiles(mediaSource, resolvedPath, browseRoot, paths);
            } else if (isImportableFile(resolvedPath)) {
                paths.add(resolvedPath);
            }
        }
    }

    private boolean shouldScanSubdirectories(MediaSource mediaSource) {
        return asBoolean(readConfig(mediaSource).get("scanSubdirectories"));
    }

    private boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean isImportableFile(String path) {
        return !"OTHER".equals(MediaTypeUtil.detect(detectContentType(fileName(path)), fileName(path)).name());
    }

    private MediaSourceBrowseResponse buildBrowseResponse(MediaSource mediaSource, String requestedPath, String browseRoot) {
        List<MediaSourceFileClient.Entry> entries = listEntries(mediaSource, requestedPath);
        MediaSourceBrowseResponse response = new MediaSourceBrowseResponse();
        response.setSourceId(mediaSource.getId());
        response.setSourceType(normalizeSourceType(mediaSource.getSourceType()));
        response.setSourceName(mediaSource.getName());
        response.setRootPath(browseRoot);
        response.setCurrentPath(requestedPath);
        response.setBoundPath(normalizeRootPath(mediaSource.getBoundPath()));
        response.setBoundPathName(resolveBoundPathName(mediaSource.getBoundPathName(), mediaSource.getBoundPath()));
        response.setItems(entries.stream()
                .sorted(Comparator.comparing(MediaSourceFileClient.Entry::isDirectory).reversed()
                        .thenComparing(MediaSourceFileClient.Entry::getName, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> toBrowseItem(mediaSource, entry))
                .toList());
        return response;
    }

    private List<MediaSourceFileClient.Entry> listEntries(MediaSource mediaSource, String path) {
        try {
            return getRequiredFileClient(normalizeSourceType(mediaSource.getSourceType()))
                    .list(buildConnection(mediaSource), path);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "读取媒体源目录失败: " + ex.getMessage());
        }
    }

    private List<ExternalMediaItemResponse> scanExternalMediaItems(MediaSource mediaSource, String startPath) {
        LinkedHashMap<String, ExternalMediaItemResponse> items = new LinkedHashMap<>();
        collectExternalMediaItems(mediaSource, startPath, browseRootPath(mediaSource, true), items, new ScanCounter());
        return List.copyOf(items.values());
    }

    private void collectExternalMediaItems(MediaSource mediaSource,
                                           String directoryPath,
                                           String browseRoot,
                                           LinkedHashMap<String, ExternalMediaItemResponse> items,
                                           ScanCounter counter) {
        if (!counter.visitedDirectories.add(directoryPath)
                || counter.directories >= MAX_RECURSIVE_SCAN_DIRECTORIES
                || items.size() >= MAX_RECURSIVE_SCAN_FILES) {
            return;
        }
        counter.directories++;
        for (MediaSourceFileClient.Entry entry : listEntries(mediaSource, directoryPath)) {
            String resolvedPath = resolveRequestedPath(mediaSource, entry.getPath(), browseRoot, true);
            if (entry.isDirectory()) {
                collectExternalMediaItems(mediaSource, resolvedPath, browseRoot, items, counter);
            } else if (isImportableFile(resolvedPath)) {
                items.putIfAbsent(resolvedPath, toExternalMediaItem(mediaSource, entry, resolvedPath));
                if (items.size() >= MAX_RECURSIVE_SCAN_FILES) {
                    return;
                }
            }
        }
    }

    private ExternalMediaItemResponse toExternalMediaItem(MediaSource mediaSource, MediaSourceFileClient.Entry entry, String normalizedPath) {
        String fileName = fileName(normalizedPath);
        String contentType = detectContentType(fileName);
        String mediaType = MediaTypeUtil.detect(contentType, fileName).name();
        ExternalMediaItemResponse item = new ExternalMediaItemResponse();
        item.setExternalMediaKey(buildExternalMediaKey(mediaSource.getId(), normalizedPath));
        item.setSourceId(mediaSource.getId());
        item.setSourceType(normalizeSourceType(mediaSource.getSourceType()));
        item.setSourceName(mediaSource.getName());
        item.setPath(normalizedPath);
        item.setFilePath(normalizedPath);
        item.setOriginUri(normalizedPath);
        item.setName(fileName);
        item.setFileName(fileName);
        item.setFolderPath(resolveFolderPath(mediaSource, normalizedPath));
        item.setContentType(contentType);
        item.setMediaType(mediaType);
        item.setSize(entry.getSize());
        item.setFileSize(entry.getSize());
        item.setModifiedAt(entry.getModifiedAt());
        item.setIngestMode(INGEST_MODE_LINKED);
        item.setStatus(MediaStatus.READY.name());
        item.setUrl(buildExternalContentUrl(mediaSource.getId(), normalizedPath));
        if ("IMAGE".equals(mediaType)) {
            item.setThumbnailUrl(buildExternalThumbnailUrl(mediaSource.getId(), normalizedPath));
        }
        return item;
    }

    private ExternalMediaScanSummary buildExternalMediaSummary(MediaSource mediaSource, String keyword) {
        ExternalMediaScanSummary summary = new ExternalMediaScanSummary();
        summary.setSourceId(mediaSource.getId());
        summary.setSourceType(normalizeSourceType(mediaSource.getSourceType()));
        summary.setSourceName(mediaSource.getName());
        summary.setBoundPath(normalizeRootPath(mediaSource.getBoundPath()));
        try {
            String root = browseRootPath(mediaSource, true);
            List<ExternalMediaItemResponse> items = scanExternalMediaItems(mediaSource, root).stream()
                    .filter(item -> !StringUtils.hasText(keyword)
                            || containsIgnoreCase(item.getFileName(), keyword)
                            || containsIgnoreCase(item.getFolderPath(), keyword)
                            || containsIgnoreCase(item.getSourceName(), keyword))
                    .toList();
            summary.setMediaCount(items.size());
            items.forEach(item -> {
                summary.getMediaTypeCounts().merge(item.getMediaType(), 1, Integer::sum);
                addFolderSummary(summary, item.getFolderPath());
            });
        } catch (Exception ex) {
            summary.setWarning(ex.getMessage());
        }
        return summary;
    }

    private void addFolderSummary(ExternalMediaScanSummary summary, String folderPath) {
        ExternalMediaScanSummary.FolderSummary folder = summary.getFolders().stream()
                .filter(item -> Objects.equals(item.getFolderPath(), folderPath))
                .findFirst()
                .orElseGet(() -> {
                    ExternalMediaScanSummary.FolderSummary created = new ExternalMediaScanSummary.FolderSummary();
                    created.setFolderPath(folderPath);
                    created.setTitle(folderTitle(folderPath));
                    summary.getFolders().add(created);
                    return created;
                });
        folder.setMediaCount(folder.getMediaCount() + 1);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String folderTitle(String folderPath) {
        if (!StringUtils.hasText(folderPath) || "/".equals(folderPath)) {
            return "根目录";
        }
        int index = folderPath.lastIndexOf('/');
        if (index >= 0 && index < folderPath.length() - 1) {
            return folderPath.substring(index + 1);
        }
        return folderPath;
    }

    private static final class ScanCounter {
        private int directories;
        private final Set<String> visitedDirectories = new LinkedHashSet<>();
    }

    private MediaSourceFileClient.Entry getFileEntry(MediaSource mediaSource, String path) {
        String normalizedPath = normalizeRootPath(path);
        String parentPath = parentPath(normalizedPath);
        return listEntries(mediaSource, parentPath).stream()
                .filter(entry -> Objects.equals(normalizeRootPath(entry.getPath()), normalizedPath))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "媒体文件不存在"));
    }

    private MediaSourceFileClient.MediaSourceConnection buildConnection(MediaSource mediaSource) {
        String sourceType = normalizeSourceType(mediaSource.getSourceType());
        ensureImplementedSourceType(sourceType);
        Map<String, Object> config = readConfig(mediaSource);
        Map<String, Object> credentials = readCredentials(mediaSource);
        return getRequiredTypeHandler(sourceType).buildConnection(mediaSource, config, credentials);
    }

    private MediaSourceTypeHandler getRequiredTypeHandler(String sourceType) {
        return typeHandlers.stream()
                .filter(handler -> Objects.equals(handler.getSourceType(), sourceType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "当前媒体源类型暂未实现"));
    }

    private MediaSource getEnabledMediaSource(Long mediaSourceId, Long userId) {
        MediaSource mediaSource = getOwnedMediaSource(mediaSourceId, userId);
        if (!Boolean.TRUE.equals(mediaSource.getEnabled())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "媒体源已停用");
        }
        return mediaSource;
    }

    private MediaSource getOwnedMediaSource(Long mediaSourceId, Long userId) {
        MediaSource mediaSource = mediaSourceMapper.selectById(mediaSourceId);
        if (mediaSource == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "媒体源不存在");
        }
        if (!userId.equals(mediaSource.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该媒体源");
        }
        return mediaSource;
    }

    private MediaSourceResponse toResponse(MediaSource mediaSource) {
        MediaSourceResponse response = new MediaSourceResponse();
        response.setId(mediaSource.getId());
        response.setName(mediaSource.getName());
        response.setSourceType(normalizeSourceType(mediaSource.getSourceType()));
        response.setConfig(readConfig(mediaSource));
        response.setConfigSummary(configSummary(mediaSource));
        response.setBoundPath(normalizeRootPath(mediaSource.getBoundPath()));
        response.setBoundPathName(resolveBoundPathName(mediaSource.getBoundPathName(), mediaSource.getBoundPath()));
        response.setEnabled(Boolean.TRUE.equals(mediaSource.getEnabled()));
        response.setPasswordConfigured(hasStoredCredentials(mediaSource));
        response.setLastScanAt(mediaSource.getLastScanAt());
        response.setCreatedAt(mediaSource.getCreatedAt());
        response.setUpdatedAt(mediaSource.getUpdatedAt());
        return response;
    }

    private MediaSourceBrowseItemResponse toBrowseItem(MediaSource mediaSource, MediaSourceFileClient.Entry entry) {
        MediaSourceBrowseItemResponse item = new MediaSourceBrowseItemResponse();
        String normalizedPath = normalizeRootPath(entry.getPath());
        item.setExternalMediaKey(buildExternalMediaKey(mediaSource.getId(), normalizedPath));
        item.setSourceId(mediaSource.getId());
        item.setSourceType(normalizeSourceType(mediaSource.getSourceType()));
        item.setSourceName(mediaSource.getName());
        item.setPath(normalizedPath);
        item.setOriginUri(normalizedPath);
        item.setName(entry.getName());
        item.setFileName(entry.getName());
        item.setDirectory(entry.isDirectory());
        item.setSize(entry.getSize());
        item.setFileSize(entry.getSize());
        item.setFolderPath(entry.isDirectory() ? normalizeRootPath(entry.getPath()) : resolveFolderPath(mediaSource, normalizedPath));
        item.setModifiedAt(entry.getModifiedAt());
        item.setHasChildren(entry.isDirectory() ? entry.isHasChildren() : null);
        if (!entry.isDirectory()) {
            String detectedContentType = detectContentType(entry.getName());
            String mediaType = MediaTypeUtil.detect(detectedContentType, entry.getName()).name();
            item.setContentType(detectedContentType);
            item.setMediaType(mediaType);
            item.setIngestMode(INGEST_MODE_LINKED);
            item.setStatus(MediaStatus.READY.name());
            if (!"OTHER".equals(mediaType) && mediaSource.getId() != null) {
                item.setUrl(buildExternalContentUrl(mediaSource.getId(), normalizedPath));
                if ("IMAGE".equals(mediaType)) {
                    item.setThumbnailUrl(buildExternalThumbnailUrl(mediaSource.getId(), normalizedPath));
                }
            }
        }
        return item;
    }

    private MediaResponse toMediaResponse(Media media) {
        MediaResponse response = new MediaResponse();
        response.setId(media.getId());
        response.setUserId(media.getUserId());
        response.setFileName(media.getFileName());
        response.setContentType(media.getContentType());
        response.setMediaType(media.getMediaType());
        response.setFileSize(media.getFileSize());
        response.setSourceType(media.getSourceType());
        response.setSourceId(media.getSourceId());
        response.setSourceName(media.getSourceName());
        response.setFolderPath(media.getFolderPath());
        response.setOriginUri(media.getOriginUri());
        response.setIngestMode(media.getIngestMode());
        response.setUrl(buildContentUrl(media.getId()));
        response.setThumbnailUrl(buildThumbnailUrl(media.getId(), media.getThumbnailKey()));
        response.setDurationSec(media.getDurationSec());
        response.setWidth(media.getWidth());
        response.setHeight(media.getHeight());
        response.setStatus(media.getStatus());
        response.setErrorMessage(media.getErrorMessage());
        response.setCreatedAt(media.getCreatedAt());
        response.setUpdatedAt(media.getUpdatedAt());
        return response;
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

    private String ensureExternalCacheObject(MediaSource mediaSource,
                                             MediaSourceFileClient.Entry entry,
                                             String normalizedPath,
                                             boolean thumbnail,
                                             String contentType,
                                             String mediaType) {
        ensureBucketReady();
        if (!thumbnail) {
            return ensureExternalContentCacheObject(mediaSource, entry, normalizedPath, contentType);
        }
        if (!"IMAGE".equals(mediaType)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前文件暂无缩略图");
        }
        return ensureExternalThumbnailCacheObject(mediaSource, entry, normalizedPath, contentType);
    }

    private String ensureExternalContentCacheObject(MediaSource mediaSource,
                                                    MediaSourceFileClient.Entry entry,
                                                    String normalizedPath,
                                                    String contentType) {
        String objectKey = buildExternalCacheObjectKey(mediaSource, entry, normalizedPath, "content", fileSuffix(entry.getName(), ".bin"));
        if (objectExists(objectKey)) {
            return objectKey;
        }
        try (InputStream inputStream = getRequiredFileClient(normalizeSourceType(mediaSource.getSourceType()))
                .open(buildConnection(mediaSource), normalizedPath)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(inputStream, -1, 10 * 1024 * 1024)
                    .build());
            return objectKey;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "缓存外部媒体内容失败: " + ex.getMessage());
        }
    }

    private String ensureExternalThumbnailCacheObject(MediaSource mediaSource,
                                                      MediaSourceFileClient.Entry entry,
                                                      String normalizedPath,
                                                      String contentType) {
        String thumbnailObjectKey = buildExternalCacheObjectKey(mediaSource, entry, normalizedPath, "thumb", ".jpg");
        if (objectExists(thumbnailObjectKey)) {
            return thumbnailObjectKey;
        }
        String contentObjectKey = ensureExternalContentCacheObject(mediaSource, entry, normalizedPath, contentType);
        try (InputStream objectStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(contentObjectKey)
                .build())) {
            BufferedImage source = ImageIO.read(objectStream);
            if (source == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "无法生成缩略图");
            }
            byte[] thumbnailBytes = buildThumbnailBytes(source);
            uploadBinary(bucket, thumbnailObjectKey, thumbnailBytes, "image/jpeg");
            return thumbnailObjectKey;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成外部媒体缩略图失败: " + ex.getMessage());
        }
    }

    private byte[] buildThumbnailBytes(BufferedImage source) throws Exception {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int longestEdge = Math.max(sourceWidth, sourceHeight);
        double scale = longestEdge > IMAGE_THUMB_MAX_EDGE
                ? (double) IMAGE_THUMB_MAX_EDGE / longestEdge
                : 1D;

        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (!ImageIO.write(thumbnail, "jpg", outputStream)) {
                throw new IllegalStateException("无法生成图片缩略图");
            }
            return outputStream.toByteArray();
        }
    }

    private void uploadBinary(String bucketName, String objectKey, byte[] data, String contentType) throws Exception {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(stream, data.length, -1)
                    .build());
        }
    }

    private boolean objectExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void ensureBucketReady() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "初始化对象存储失败");
        }
    }

    private String buildObjectKey(Long userId, String fileName) {
        String safeName = fileName == null ? "unknown.bin" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "media/" + userId + "/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + safeName;
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

    private String buildExternalContentUrl(Long mediaSourceId, String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/media-sources/{id}/content")
                .queryParam("path", normalizeRootPath(path))
                .buildAndExpand(mediaSourceId)
                .toUriString();
    }

    private String buildExternalThumbnailUrl(Long mediaSourceId, String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/media-sources/{id}/thumbnail")
                .queryParam("path", normalizeRootPath(path))
                .buildAndExpand(mediaSourceId)
                .toUriString();
    }

    private String buildExternalMediaKey(Long mediaSourceId, String path) {
        if (mediaSourceId == null || !StringUtils.hasText(path)) {
            return null;
        }
        String payload = mediaSourceId + ":" + normalizeRootPath(path);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String buildExternalCacheObjectKey(MediaSource mediaSource,
                                               MediaSourceFileClient.Entry entry,
                                               String path,
                                               String variant,
                                               String suffix) {
        String fingerprint = Objects.toString(mediaSource.getId(), "draft")
                + "|" + normalizeRootPath(path)
                + "|" + Objects.toString(entry.getSize(), "0")
                + "|" + Objects.toString(entry.getModifiedAt(), "")
                + "|" + variant;
        return EXTERNAL_CACHE_PREFIX
                + Objects.toString(mediaSource.getId(), "draft")
                + "/"
                + sha256Hex(fingerprint)
                + suffix;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成外部媒体缓存键失败");
        }
    }

    private String fileSuffix(String fileName, String defaultSuffix) {
        if (!StringUtils.hasText(fileName)) {
            return defaultSuffix;
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return defaultSuffix;
        }
        String suffix = fileName.substring(index).toLowerCase(Locale.ROOT);
        return suffix.length() <= 12 ? suffix : defaultSuffix;
    }

    private String detectContentType(String fileName) {
        String detected = tika.detect(fileName == null ? "unknown.bin" : fileName);
        return StringUtils.hasText(detected) ? detected.toLowerCase(Locale.ROOT) : "application/octet-stream";
    }

    private Boolean resolveEnabled(Boolean enabled) {
        return enabled == null ? Boolean.TRUE : enabled;
    }

    private void ensureSupportedSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType) || !EXTERNAL_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的媒体源类型");
        }
    }

    private void ensureImplementedSourceType(String sourceType) {
        getRequiredTypeHandler(sourceType);
    }

    private String normalizeSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase(Locale.ROOT) : null;
    }

    private Map<String, Object> normalizeConfig(String sourceType, Map<String, Object> config) {
        return getRequiredTypeHandler(sourceType).normalizeConfig(config);
    }

    private Map<String, Object> normalizeCredentials(String sourceType, Map<String, Object> credentials, boolean requirePassword) {
        return getRequiredTypeHandler(sourceType).normalizeCredentials(credentials, requirePassword);
    }

    private void applySourceDefinition(MediaSource mediaSource, Map<String, Object> config, Map<String, Object> credentials) {
        mediaSource.setConfigJson(writeJson(config));
        mediaSource.setCredentialCiphertext(CollectionUtils.isEmpty(credentials)
                ? null
                : credentialCryptoService.encrypt(writeJson(credentials)));
        getRequiredTypeHandler(normalizeSourceType(mediaSource.getSourceType()))
                .applyPersistentFields(mediaSource, config, credentials);
    }

    private Map<String, Object> readConfig(MediaSource mediaSource) {
        if (StringUtils.hasText(mediaSource.getConfigJson())) {
            Map<String, Object> config = parseJsonMap(mediaSource.getConfigJson(), "解析媒体源配置失败");
            if ("SMB".equalsIgnoreCase(mediaSource.getSourceType())) {
                normalizeLegacySmbConfig(config);
            }
            return config;
        }
        Map<String, Object> config = new LinkedHashMap<>();
        if (StringUtils.hasText(mediaSource.getHost())) {
            config.put("host", mediaSource.getHost());
        }
        if (mediaSource.getPort() != null) {
            config.put("port", mediaSource.getPort());
        }
        String rootPath = normalizeRootPath(mediaSource.getRootPath());
        if (StringUtils.hasText(mediaSource.getShareName())) {
            String shareName = normalizeRootPath(mediaSource.getShareName());
            rootPath = "/".equals(rootPath) ? shareName : normalizeRootPath(shareName + rootPath);
        }
        config.put("rootPath", rootPath);
        return config;
    }

    private void normalizeLegacySmbConfig(Map<String, Object> config) {
        String shareName = asText(config.get("shareName"));
        if (!StringUtils.hasText(shareName)) {
            return;
        }
        String rootPath = normalizeRootPath(asText(config.get("rootPath")));
        String normalizedShareName = normalizeRootPath(shareName);
        config.put("rootPath", "/".equals(rootPath) ? normalizedShareName : normalizeRootPath(normalizedShareName + rootPath));
        config.remove("shareName");
    }

    private Map<String, Object> readCredentials(MediaSource mediaSource) {
        if (StringUtils.hasText(mediaSource.getCredentialCiphertext())) {
            String plainText = credentialCryptoService.decrypt(mediaSource.getCredentialCiphertext());
            return parseJsonMap(plainText, "解析媒体源凭证失败");
        }
        Map<String, Object> credentials = new LinkedHashMap<>();
        if (StringUtils.hasText(mediaSource.getUsername())) {
            credentials.put("username", mediaSource.getUsername());
        }
        if (StringUtils.hasText(mediaSource.getPasswordCiphertext())) {
            credentials.put("password", credentialCryptoService.decrypt(mediaSource.getPasswordCiphertext()));
        }
        return credentials;
    }

    private MediaSourceFileClient getRequiredFileClient(String sourceType) {
        return mediaSourceFileClients.stream()
                .filter(client -> Objects.equals(client.getSourceType(), sourceType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "当前媒体源文件客户端暂未实现"));
    }

    private Map<String, Object> configSummary(MediaSource mediaSource) {
        Map<String, Object> config = readConfig(mediaSource);
        return getRequiredTypeHandler(normalizeSourceType(mediaSource.getSourceType())).summarizeConfig(config);
    }

    private boolean hasStoredCredentials(MediaSource mediaSource) {
        return StringUtils.hasText(mediaSource.getCredentialCiphertext()) || StringUtils.hasText(mediaSource.getPasswordCiphertext());
    }

    private String browseRootPath(MediaSource mediaSource, boolean boundOnly) {
        if (boundOnly && StringUtils.hasText(mediaSource.getBoundPath())) {
            return normalizeRootPath(mediaSource.getBoundPath());
        }
        Object rootPath = readConfig(mediaSource).get("rootPath");
        return normalizeRootPath(asText(rootPath));
    }

    private String resolveRequestedPath(MediaSource mediaSource, String path, String browseRoot, boolean boundOnly) {
        String accessibleRoot = browseRootPath(mediaSource, boundOnly);
        String candidate = StringUtils.hasText(path) ? normalizeRootPath(path) : normalizeRootPath(browseRoot);
        if (!isPathWithinRoot(candidate, accessibleRoot)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, boundOnly ? "访问路径超出已绑定目录范围" : "访问路径超出媒体源根目录范围");
        }
        for (String segment : candidate.split("/")) {
            if ("..".equals(segment)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "访问路径非法");
            }
        }
        return candidate;
    }

    private boolean isPathWithinRoot(String candidate, String rootPath) {
        if ("/".equals(rootPath)) {
            return true;
        }
        return Objects.equals(candidate, rootPath) || candidate.startsWith(rootPath + "/");
    }

    private String normalizeRootPath(String rootPath) {
        if (!StringUtils.hasText(rootPath)) {
            return "/";
        }
        String normalized = rootPath.trim().replace('\\', '/');
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

    private String resolveFolderPath(MediaSource mediaSource, String sourcePath) {
        String basePath = browseRootPath(mediaSource, true);
        String normalizedPath = resolveRequestedPath(mediaSource, sourcePath, basePath, true);
        int slashIndex = normalizedPath.lastIndexOf('/');
        String parent = slashIndex <= 0 ? "/" : normalizedPath.substring(0, slashIndex);
        String relative = Objects.equals(parent, basePath)
                ? ""
                : "/".equals(basePath) ? parent : parent.substring(basePath.length());
        String resolved = "/" + mediaSource.getName() + (StringUtils.hasText(relative) ? relative : "");
        return normalizeRootPath(resolved);
    }

    private String resolveBoundPathName(String boundPathName, String boundPath) {
        if (StringUtils.hasText(boundPathName)) {
            return boundPathName.trim();
        }
        String normalizedPath = normalizeRootPath(boundPath);
        return "/".equals(normalizedPath) ? "根目录" : fileName(normalizedPath);
    }

    private String fileName(String path) {
        String normalized = normalizeRootPath(path);
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private String parentPath(String path) {
        String normalized = normalizeRootPath(path);
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex <= 0) {
            return "/";
        }
        return normalized.substring(0, slashIndex);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> parseJsonMap(String json, String message) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, message);
        }
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "序列化媒体源配置失败");
        }
    }
}
