package com.cloudalbum.publisher.focalpoint.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.album.entity.Album;
import com.cloudalbum.publisher.album.entity.AlbumMedia;
import com.cloudalbum.publisher.album.mapper.AlbumMapper;
import com.cloudalbum.publisher.album.mapper.AlbumMediaMapper;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.util.CryptoUtil;
import com.cloudalbum.publisher.focalpoint.dto.*;
import com.cloudalbum.publisher.focalpoint.entity.VisionLlmConfig;
import com.cloudalbum.publisher.focalpoint.mapper.VisionLlmConfigMapper;
import com.cloudalbum.publisher.focalpoint.provider.FocalPointProvider;
import com.cloudalbum.publisher.focalpoint.provider.FocalPointProviderRegistry;
import com.cloudalbum.publisher.focalpoint.provider.FocalPointResult;
import com.cloudalbum.publisher.focalpoint.service.FocalPointService;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.mediasource.service.MediaSourceService;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FocalPointServiceImpl implements FocalPointService {

    private final AlbumMapper albumMapper;
    private final AlbumMediaMapper albumMediaMapper;
    private final MediaMapper mediaMapper;
    private final VisionLlmConfigMapper visionLlmConfigMapper;
    private final FocalPointProviderRegistry providerRegistry;
    private final MediaSourceService mediaSourceService;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Override
    @Transactional
    public void updateAlbumSettings(Long albumId, Long userId, AlbumFocalPointSettingsRequest request) {
        Album album = getOwnedAlbum(albumId, userId);
        if (request.getFocalPointEnabled() != null) {
            album.setFocalPointEnabled(request.getFocalPointEnabled());
        }
        if (StringUtils.hasText(request.getFocalPointProvider())) {
            String providerType = request.getFocalPointProvider().toUpperCase(Locale.ROOT);
            List<String> available = providerRegistry.getAvailableProviderTypes();
            if (!available.contains(providerType)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "不支持的焦点检测 Provider: " + providerType + "，可用: " + String.join(", ", available));
            }
            album.setFocalPointProvider(providerType);
        }
        albumMapper.updateById(album);
    }

    @Override
    @Transactional
    public void updateFocalPoint(Long albumId, Long contentId, Long userId, FocalPointUpdateRequest request) {
        getOwnedAlbum(albumId, userId);
        AlbumMedia albumMedia = getOwnedAlbumMedia(contentId, albumId);
        albumMedia.setFocalPointX(request.getX());
        albumMedia.setFocalPointY(request.getY());
        albumMedia.setFocalPointProvider("MANUAL");
        albumMedia.setFocalPointConfidence(1.0);
        albumMedia.setFocalPointRegionType("CENTER");
        albumMedia.setFocalPointRegionWidth(0.0);
        albumMedia.setFocalPointRegionHeight(0.0);
        albumMedia.setFocalPointUpdatedAt(LocalDateTime.now());
        albumMediaMapper.updateById(albumMedia);
    }

    @Override
    @Transactional
    public void clearFocalPoint(Long albumId, Long contentId, Long userId) {
        getOwnedAlbum(albumId, userId);
        AlbumMedia albumMedia = getOwnedAlbumMedia(contentId, albumId);
        albumMedia.setFocalPointX(null);
        albumMedia.setFocalPointY(null);
        albumMedia.setFocalPointProvider(null);
        albumMedia.setFocalPointConfidence(null);
        albumMedia.setFocalPointRegionType(null);
        albumMedia.setFocalPointRegionWidth(null);
        albumMedia.setFocalPointRegionHeight(null);
        albumMedia.setFocalPointUpdatedAt(LocalDateTime.now());
        albumMediaMapper.updateById(albumMedia);
    }

    @Override
    @Transactional
    public FocalPointProcessResult batchProcess(Long albumId, Long userId, FocalPointBatchProcessRequest request) {
        Album album = getOwnedAlbum(albumId, userId);
        String providerType = resolveProviderType(request.getProviderType(), album);
        FocalPointProvider provider = providerRegistry.getProvider(providerType);

        Map<String, Object> extraConfig = buildExtraConfig(request.getVisionLlmConfigId(), userId);

        List<AlbumMedia> mediaItems;
        if (request.getContentIds() != null && !request.getContentIds().isEmpty()) {
            mediaItems = albumMediaMapper.selectBatchIds(request.getContentIds());
            mediaItems = mediaItems.stream()
                    .filter(m -> Objects.equals(m.getAlbumId(), albumId))
                    .collect(Collectors.toList());
        } else {
            mediaItems = albumMediaMapper.selectList(new LambdaQueryWrapper<AlbumMedia>()
                    .eq(AlbumMedia::getAlbumId, albumId)
                    .eq(AlbumMedia::getMediaType, "IMAGE"));
        }

        FocalPointProcessResult result = new FocalPointProcessResult();
        result.setTotalItems(mediaItems.size());
        log.info("FocalPoint: Batch processing album {} with provider {}, {} items to process", albumId, providerType, mediaItems.size());

        for (AlbumMedia item : mediaItems) {
            try {
                log.info("FocalPoint: Processing item {} (mediaId={}, mediaType={}, isInternal={}, isExternal={}, sourceId={}, filePath={})",
                        item.getId(), item.getMediaId(), item.getMediaType(), item.isInternal(), item.isExternal(),
                        item.getSourceId(), item.getFilePath());
                boolean processed = processSingleItem(item, provider, extraConfig, userId);
                if (processed) {
                    result.setProcessedItems(result.getProcessedItems() + 1);
                    log.info("FocalPoint: Item {} processed successfully", item.getId());
                } else {
                    result.setSkippedItems(result.getSkippedItems() + 1);
                    log.info("FocalPoint: Item {} skipped", item.getId());
                }
            } catch (Exception e) {
                result.setFailedItems(result.getFailedItems() + 1);
                result.getErrors().add(item.getId() + ": " + e.getMessage());
                log.warn("FocalPoint: Detection failed for albumMedia {}: {}", item.getId(), e.getMessage());
            }
        }
        log.info("FocalPoint: Batch processing complete for album {}: total={}, processed={}, skipped={}, failed={}",
                albumId, result.getTotalItems(), result.getProcessedItems(), result.getSkippedItems(), result.getFailedItems());
        return result;
    }

    @Override
    public void autoProcessIfEnabled(Long albumId, Long albumMediaId) {
        AlbumMedia albumMedia = albumMediaMapper.selectById(albumMediaId);
        if (albumMedia == null) {
            return;
        }
        if (!"IMAGE".equals(albumMedia.getMediaType())) {
            return;
        }

        Album album = albumMapper.selectById(albumId);
        if (album == null || !Boolean.TRUE.equals(album.getFocalPointEnabled())) {
            return;
        }
        if (!StringUtils.hasText(album.getFocalPointProvider()) || "NOOP".equals(album.getFocalPointProvider())) {
            return;
        }

        try {
            FocalPointProvider provider = providerRegistry.getProvider(album.getFocalPointProvider());
            boolean processed = processSingleItem(albumMedia, provider, Collections.emptyMap(), album.getUserId());
            if (processed) {
                log.info("Auto-detected focal point for albumMedia {} in album {}", albumMediaId, albumId);
            }
        } catch (Exception e) {
            log.warn("Auto focal point detection failed for albumMedia {}: {}", albumMediaId, e.getMessage());
        }
    }

    @Override
    public List<String> getAvailableProviderTypes() {
        return providerRegistry.getAvailableProviderTypes();
    }

    // ==================== LLM Config CRUD ====================

    @Override
    public List<VisionLlmConfigResponse> listLlmConfigs(Long userId) {
        return visionLlmConfigMapper.selectList(new LambdaQueryWrapper<VisionLlmConfig>()
                .eq(VisionLlmConfig::getUserId, userId)
                .orderByDesc(VisionLlmConfig::getCreatedAt))
                .stream().map(this::toLlmConfigResponse).collect(Collectors.toList());
    }

    @Override
    public VisionLlmConfigResponse getLlmConfig(Long id, Long userId) {
        VisionLlmConfig config = getOwnedLlmConfig(id, userId);
        return toLlmConfigResponse(config);
    }

    @Override
    @Transactional
    public VisionLlmConfigResponse createLlmConfig(Long userId, VisionLlmConfigRequest request) {
        VisionLlmConfig config = new VisionLlmConfig();
        config.setUserId(userId);
        applyLlmConfigFields(config, request);
        visionLlmConfigMapper.insert(config);
        return toLlmConfigResponse(config);
    }

    @Override
    @Transactional
    public VisionLlmConfigResponse updateLlmConfig(Long id, Long userId, VisionLlmConfigRequest request) {
        VisionLlmConfig config = getOwnedLlmConfig(id, userId);
        applyLlmConfigFields(config, request);
        visionLlmConfigMapper.updateById(config);
        return toLlmConfigResponse(config);
    }

    @Override
    @Transactional
    public void deleteLlmConfig(Long id, Long userId) {
        VisionLlmConfig config = getOwnedLlmConfig(id, userId);
        visionLlmConfigMapper.deleteById(config.getId());
    }

    // ==================== Internal ====================

    private boolean processSingleItem(AlbumMedia item, FocalPointProvider provider, Map<String, Object> extraConfig, Long userId) {
        try (InputStream imageStream = resolveImageStream(item, userId)) {
            if (imageStream == null) {
                return false;
            }

            InputStream detectionStream;
            if ("VISION_LLM".equals(provider.getProviderType())) {
                detectionStream = resizeForLlm(imageStream);
            } else {
                detectionStream = imageStream;
            }

            List<FocalPointResult> results = provider.detect(detectionStream, extraConfig);
            if (results.isEmpty()) {
                return false;
            }

            FocalPointResult best = selectBestFocalPoint(results);
            item.setFocalPointX(best.getX());
            item.setFocalPointY(best.getY());
            item.setFocalPointProvider(provider.getProviderType());
            item.setFocalPointConfidence(best.getConfidence());
            item.setFocalPointRegionType(best.getRegionType());
            item.setFocalPointRegionWidth(best.getRegionWidth());
            item.setFocalPointRegionHeight(best.getRegionHeight());
            item.setFocalPointUpdatedAt(LocalDateTime.now());
            albumMediaMapper.updateById(item);
            return true;
        } catch (Exception e) {
            log.warn("Focal point detection failed for {}: {}", item.getId(), e.getMessage());
            return false;
        }
    }

    private InputStream resolveImageStream(AlbumMedia item, Long userId) {
        if (item.isInternal()) {
            Media media = mediaMapper.selectById(item.getMediaId());
            if (media == null) {
                log.info("FocalPoint: Media not found for albumMedia {}, mediaId={}", item.getId(), item.getMediaId());
                return null;
            }
            // Try objectKey first, fall back to originUri
            String objectKey = StringUtils.hasText(media.getObjectKey()) ? media.getObjectKey() : media.getOriginUri();
            if (!StringUtils.hasText(objectKey)) {
                log.info("FocalPoint: No objectKey or originUri for albumMedia {}, mediaId={}", item.getId(), item.getMediaId());
                return null;
            }
            try {
                String resolvedBucket = StringUtils.hasText(media.getBucketName()) ? media.getBucketName() : bucket;
                log.info("FocalPoint: Fetching media from MinIO: bucket={}, objectKey={}", resolvedBucket, objectKey);
                return minioClient.getObject(GetObjectArgs.builder()
                        .bucket(resolvedBucket)
                        .object(objectKey).build());
            } catch (Exception e) {
                log.warn("FocalPoint: Failed to get media from MinIO: bucket={}, objectKey={}, error={}",
                        StringUtils.hasText(media.getBucketName()) ? media.getBucketName() : bucket,
                        objectKey, e.getMessage());
                return null;
            }
        }
        if (item.isExternal()) {
            if (item.getSourceId() == null || !StringUtils.hasText(item.getFilePath())) {
                log.info("FocalPoint: External media {} missing sourceId or filePath", item.getId());
                return null;
            }
            try {
                log.info("FocalPoint: Opening external media from source {}, path={}", item.getSourceId(), item.getFilePath());
                return mediaSourceService.openExternalContent(item.getSourceId(), userId, item.getFilePath());
            } catch (Exception e) {
                log.warn("FocalPoint: Failed to open external media {}: {}", item.getId(), e.getMessage());
                return null;
            }
        }
        return null;
    }

    private FocalPointResult selectBestFocalPoint(List<FocalPointResult> results) {
        return results.stream()
                .max(Comparator.comparingDouble(FocalPointResult::getConfidence)
                        .thenComparingDouble(r -> -distanceToCenter(r)))
                .orElse(results.get(0));
    }

    private double distanceToCenter(FocalPointResult r) {
        double dx = r.getX() - 0.5;
        double dy = r.getY() - 0.5;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private String resolveProviderType(String requested, Album album) {
        if (StringUtils.hasText(requested)) {
            return requested.toUpperCase(Locale.ROOT);
        }
        if (StringUtils.hasText(album.getFocalPointProvider())) {
            return album.getFocalPointProvider();
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "未指定焦点检测 Provider");
    }

    private Map<String, Object> buildExtraConfig(Long visionLlmConfigId, Long userId) {
        Map<String, Object> config = new HashMap<>();
        if (visionLlmConfigId != null) {
            VisionLlmConfig llmConfig = getOwnedLlmConfig(visionLlmConfigId, userId);
            config.put("apiEndpoint", llmConfig.getApiEndpoint());
            config.put("apiKey", CryptoUtil.decrypt(llmConfig.getApiKeyEncrypted()));
            config.put("modelName", llmConfig.getModelName());
            config.put("maxTokens", llmConfig.getMaxTokens());
            config.put("timeoutSeconds", llmConfig.getTimeoutSeconds());
            if (StringUtils.hasText(llmConfig.getExtraParams())) {
                config.put("extraParams", llmConfig.getExtraParams());
            }
        }
        return config;
    }

    private InputStream resizeForLlm(InputStream original) throws IOException {
        BufferedImage image = ImageIO.read(original);
        if (image == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        int maxDim = 1024;
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= maxDim && h <= maxDim) {
            // Convert to RGB (remove alpha channel) with white background
            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().setColor(java.awt.Color.WHITE);
            rgb.getGraphics().fillRect(0, 0, w, h);
            rgb.getGraphics().drawImage(image, 0, 0, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", bos);
            return new ByteArrayInputStream(bos.toByteArray());
        }
        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);
        java.awt.Image scaled = image.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
        BufferedImage output = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        output.getGraphics().setColor(java.awt.Color.WHITE);
        output.getGraphics().fillRect(0, 0, newW, newH);
        output.getGraphics().drawImage(scaled, 0, 0, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(output, "jpg", bos);
        return new ByteArrayInputStream(bos.toByteArray());
    }

    private Album getOwnedAlbum(Long albumId, Long userId) {
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "相册不存在");
        }
        if (!Objects.equals(album.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return album;
    }

    private AlbumMedia getOwnedAlbumMedia(Long contentId, Long albumId) {
        AlbumMedia media = albumMediaMapper.selectById(contentId);
        if (media == null || !Objects.equals(media.getAlbumId(), albumId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "相册内容不存在");
        }
        return media;
    }

    private VisionLlmConfig getOwnedLlmConfig(Long id, Long userId) {
        VisionLlmConfig config = visionLlmConfigMapper.selectById(id);
        if (config == null || !Objects.equals(config.getUserId(), userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "LLM 配置不存在");
        }
        return config;
    }

    private void applyLlmConfigFields(VisionLlmConfig config, VisionLlmConfigRequest request) {
        config.setName(request.getName());
        config.setApiEndpoint(request.getApiEndpoint());
        if (StringUtils.hasText(request.getApiKey())) {
            config.setApiKeyEncrypted(CryptoUtil.encrypt(request.getApiKey()));
        }
        config.setModelName(request.getModelName());
        config.setMaxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 1024);
        config.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 30);
        config.setExtraParams(request.getExtraParams());
        config.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
    }

    private VisionLlmConfigResponse toLlmConfigResponse(VisionLlmConfig config) {
        VisionLlmConfigResponse resp = new VisionLlmConfigResponse();
        resp.setId(config.getId());
        resp.setName(config.getName());
        resp.setApiEndpoint(config.getApiEndpoint());
        resp.setApiKeyMasked(CryptoUtil.mask(config.getApiKeyEncrypted()));
        resp.setModelName(config.getModelName());
        resp.setMaxTokens(config.getMaxTokens());
        resp.setTimeoutSeconds(config.getTimeoutSeconds());
        resp.setExtraParams(config.getExtraParams());
        resp.setEnabled(config.getEnabled());
        resp.setCreatedAt(config.getCreatedAt());
        resp.setUpdatedAt(config.getUpdatedAt());
        return resp;
    }
}
