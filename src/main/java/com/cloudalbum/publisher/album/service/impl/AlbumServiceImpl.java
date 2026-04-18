package com.cloudalbum.publisher.album.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudalbum.publisher.album.dto.*;
import com.cloudalbum.publisher.album.entity.Album;
import com.cloudalbum.publisher.album.entity.AlbumMedia;
import com.cloudalbum.publisher.album.mapper.AlbumMapper;
import com.cloudalbum.publisher.album.mapper.AlbumMediaMapper;
import com.cloudalbum.publisher.album.service.AlbumService;
import com.cloudalbum.publisher.common.enums.MediaStatus;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.media.content.MediaHttpWriter;
import com.cloudalbum.publisher.media.content.ObjectStorageMediaContentResolver;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseItemResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseResponse;
import com.cloudalbum.publisher.mediasource.service.MediaSourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlbumServiceImpl implements AlbumService {

    private final AlbumMapper albumMapper;
    private final AlbumMediaMapper albumMediaMapper;
    private final MediaMapper mediaMapper;
    private final MediaSourceService mediaSourceService;
    private final ObjectStorageMediaContentResolver objectStorageMediaContentResolver;
    private final MediaHttpWriter mediaHttpWriter;

    @org.springframework.beans.factory.annotation.Value("${minio.bucket}")
    private String bucket;

    @Override
    public PageResult<AlbumResponse> listAlbums(Long userId, PageRequest pageRequest, String visibility) {
        LambdaQueryWrapper<Album> queryWrapper = new LambdaQueryWrapper<Album>()
                .eq(Album::getUserId, userId)
                .orderByDesc(Album::getCreatedAt);
        if (StringUtils.hasText(visibility)) {
            queryWrapper.eq(Album::getVisibility, visibility);
        }
        IPage<Album> page = albumMapper.selectPage(
                new Page<>(pageRequest.getPage(), pageRequest.getSize()),
                queryWrapper);
        List<AlbumResponse> list = page.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageRequest.getPage(), pageRequest.getSize(), list);
    }

    @Override
    public AlbumResponse getAlbum(Long albumId, Long currentUserId) {
        Album album = getAlbumOrThrow(albumId);
        checkAccess(album, currentUserId);
        return toResponse(album);
    }

    @Override
    public AlbumResponse createAlbum(Long userId, AlbumCreateRequest request) {
        Album album = new Album();
        album.setUserId(userId);
        album.setTitle(request.getTitle());
        album.setDescription(request.getDescription());
        album.setVisibility(request.getVisibility());
        album.setStatus("DRAFT");
        album.setSortOrder(0);
        album.setBgmVolume(80);
        albumMapper.insert(album);
        return toResponse(album);
    }

    @Override
    public AlbumResponse updateAlbum(Long albumId, Long userId, AlbumUpdateRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        if (StringUtils.hasText(request.getTitle())) album.setTitle(request.getTitle());
        if (request.getDescription() != null) album.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getVisibility())) album.setVisibility(request.getVisibility());
        if (StringUtils.hasText(request.getStatus())) album.setStatus(request.getStatus());
        if (request.getSortOrder() != null) album.setSortOrder(request.getSortOrder());
        albumMapper.updateById(album);
        return toResponse(album);
    }

    @Override
    @Transactional
    public void deleteAlbum(Long albumId, Long userId) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        albumMapper.deleteById(albumId);
        albumMediaMapper.delete(new LambdaQueryWrapper<AlbumMedia>()
                .eq(AlbumMedia::getAlbumId, albumId));
    }

    @Override
    public PageResult<AlbumContentResponse> listContents(Long albumId, Long userId, PageRequest pageRequest) {
        Album album = getAlbumOrThrow(albumId);
        checkAccess(album, userId);
        IPage<AlbumMedia> page = albumMediaMapper.selectPage(
                new Page<>(pageRequest.getPage(), pageRequest.getSize()),
                new LambdaQueryWrapper<AlbumMedia>()
                        .eq(AlbumMedia::getAlbumId, albumId)
                        .orderByAsc(AlbumMedia::getSortOrder)
                        .orderByAsc(AlbumMedia::getId));
        Map<Long, Media> mediaMap = loadInternalMediaMap(page.getRecords());
        List<AlbumContentResponse> list = page.getRecords().stream()
                .map(item -> toContentResponse(item, mediaMap.get(item.getMediaId())))
                .toList();
        return PageResult.of(page.getTotal(), pageRequest.getPage(), pageRequest.getSize(), list);
    }

    @Override
    @Transactional
    public AlbumContentResponse addContent(Long albumId, Long userId, AlbumAddContentRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        AlbumMedia albumMedia = request.isExternal()
                ? addExternalContent(albumId, userId, request)
                : addInternalContent(albumId, userId, request);
        Media media = albumMedia.getMediaId() != null ? mediaMapper.selectById(albumMedia.getMediaId()) : null;
        return toContentResponse(albumMedia, media);
    }

    @Override
    @Transactional
    public List<AlbumContentResponse> addContents(Long albumId, Long userId, List<AlbumAddContentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "待添加媒体不能为空");
        }
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);

        Set<String> requestKeys = new HashSet<>();
        for (AlbumAddContentRequest request : requests) {
            String uniqueKey = request.isExternal()
                    ? "external:" + request.getExternalMediaKey()
                    : "internal:" + request.getMediaId();
            if (!requestKeys.add(uniqueKey)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "批量添加中存在重复媒体");
            }
        }

        List<AlbumContentResponse> responses = new ArrayList<>();
        for (AlbumAddContentRequest request : requests) {
            AlbumMedia albumMedia = request.isExternal()
                    ? addExternalContent(albumId, userId, request)
                    : addInternalContent(albumId, userId, request);
            Media media = albumMedia.getMediaId() != null ? mediaMapper.selectById(albumMedia.getMediaId()) : null;
            responses.add(toContentResponse(albumMedia, media));
        }
        return responses;
    }

    @Override
    @Transactional
    public void removeContent(Long albumId, Long contentId, Long userId) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);

        AlbumMedia albumMedia = albumMediaMapper.selectById(contentId);
        if (albumMedia == null || !albumId.equals(albumMedia.getAlbumId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "相册内容不存在");
        }

        albumMediaMapper.deleteById(contentId);
    }

    @Override
    @Transactional
    public AlbumResponse updateCover(Long albumId, Long userId, AlbumCoverRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        if (request.isExternal()) {
            MediaSourceBrowseItemResponse externalItem = resolveExternalMediaItem(
                    request.getSourceId(),
                    userId,
                    request.getPath(),
                    request.getExternalMediaKey());
            if (!isExternalCoverEligible(externalItem)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "封面媒体仅支持图片");
            }
            album.setCoverMediaId(null);
            album.setCoverSourceId(externalItem.getSourceId());
            album.setCoverSourceType(externalItem.getSourceType());
            album.setCoverSourceName(externalItem.getSourceName());
            album.setCoverExternalMediaKey(externalItem.getExternalMediaKey());
            album.setCoverPath(externalItem.getPath());
            album.setCoverFileName(firstText(externalItem.getFileName(), externalItem.getName()));
            album.setCoverContentType(externalItem.getContentType());
            album.setCoverMediaType(externalItem.getMediaType());
            album.setCoverUrl(buildAlbumCoverUrl(album.getId()));
        } else {
            if (request.getMediaId() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "封面媒体不能为空");
            }
            Media media = getOwnedReadyMedia(request.getMediaId(), userId);
            if (!isCoverEligible(media)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "封面媒体仅支持图片或视频");
            }
            if ("VIDEO".equals(media.getMediaType()) && !StringUtils.hasText(media.getThumbnailKey())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "当前视频暂无可用缩略图");
            }
            album.setCoverMediaId(media.getId());
            album.setCoverSourceId(null);
            album.setCoverSourceType(null);
            album.setCoverSourceName(null);
            album.setCoverExternalMediaKey(null);
            album.setCoverPath(null);
            album.setCoverFileName(null);
            album.setCoverContentType(null);
            album.setCoverMediaType(null);
            album.setCoverUrl(buildLegacyCoverStorageValue(media));
        }
        albumMapper.updateById(album);
        return toResponse(album);
    }

    @Override
    public AlbumResponse updateBgm(Long albumId, Long userId, AlbumBgmRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        if (request.getBgmUrl() != null) album.setBgmUrl(request.getBgmUrl());
        if (request.getBgmVolume() != null) album.setBgmVolume(request.getBgmVolume());
        albumMapper.updateById(album);
        return toResponse(album);
    }

    @Override
    public void writeAlbumCover(Long albumId, Long userId, HttpServletRequest request, HttpServletResponse response) {
        Album album = getAlbumOrThrow(albumId);
        checkAccess(album, userId);
        if (hasExternalCover(album)) {
            mediaSourceService.writeMediaContent(
                    album.getCoverSourceId(),
                    album.getUserId(),
                    album.getCoverPath(),
                    shouldUseExternalThumbnailForCover(album),
                    request,
                    response);
            return;
        }

        Media coverMedia = resolveCoverMedia(album);
        if (coverMedia != null) {
            mediaHttpWriter.write(
                    objectStorageMediaContentResolver.resolve(coverMedia, shouldUseThumbnailForCover(coverMedia)),
                    request,
                    response,
                    "读取相册封面失败");
            return;
        }

        String objectKey = resolveCoverObjectKey(album.getCoverUrl());
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "相册封面不存在");
        }

        mediaHttpWriter.write(
                objectStorageMediaContentResolver.resolveObject(bucket, objectKey, "application/octet-stream", "相册封面不存在"),
                request,
                response,
                "读取相册封面失败");
    }

    private AlbumMedia addInternalContent(Long albumId, Long userId, AlbumAddContentRequest request) {
        if (request.getMediaId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "媒体ID不能为空");
        }
        Media media = getOwnedReadyMedia(request.getMediaId(), userId);
        if (albumMediaMapper.selectCount(new LambdaQueryWrapper<AlbumMedia>()
                .eq(AlbumMedia::getAlbumId, albumId)
                .eq(AlbumMedia::getMediaId, request.getMediaId())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该媒体已在相册中");
        }
        AlbumMedia albumMedia = new AlbumMedia();
        albumMedia.setAlbumId(albumId);
        albumMedia.setMediaId(request.getMediaId());
        albumMedia.setSortOrder(defaultSortOrder(request.getSortOrder()));
        albumMedia.setDuration(defaultDuration(request.getDuration()));
        albumMedia.setFileName(media.getFileName());
        albumMedia.setContentType(media.getContentType());
        albumMedia.setMediaType(media.getMediaType());
        albumMediaMapper.insert(albumMedia);
        return albumMedia;
    }

    private AlbumMedia addExternalContent(Long albumId, Long userId, AlbumAddContentRequest request) {
        MediaSourceBrowseItemResponse externalItem = resolveExternalMediaItem(
                request.getSourceId(),
                userId,
                request.getPath(),
                request.getExternalMediaKey());
        if (albumMediaMapper.selectCount(new LambdaQueryWrapper<AlbumMedia>()
                .eq(AlbumMedia::getAlbumId, albumId)
                .eq(AlbumMedia::getExternalMediaKey, externalItem.getExternalMediaKey())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该媒体已在相册中");
        }
        AlbumMedia albumMedia = new AlbumMedia();
        albumMedia.setAlbumId(albumId);
        albumMedia.setSourceId(externalItem.getSourceId());
        albumMedia.setSourceType(externalItem.getSourceType());
        albumMedia.setSourceName(externalItem.getSourceName());
        albumMedia.setExternalMediaKey(externalItem.getExternalMediaKey());
        albumMedia.setFilePath(externalItem.getPath());
        albumMedia.setFileName(firstText(externalItem.getFileName(), externalItem.getName()));
        albumMedia.setContentType(externalItem.getContentType());
        albumMedia.setMediaType(externalItem.getMediaType());
        albumMedia.setSortOrder(defaultSortOrder(request.getSortOrder()));
        albumMedia.setDuration(defaultDuration(request.getDuration()));
        albumMediaMapper.insert(albumMedia);
        return albumMedia;
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
                .filter(candidate -> Objects.equals(normalizeExternalPath(candidate.getPath()), normalizedPath))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "外部媒体不存在或不可访问"));
        if (!StringUtils.hasText(item.getExternalMediaKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前外部文件不支持绑定到相册");
        }
        if (StringUtils.hasText(externalMediaKey) && !Objects.equals(externalMediaKey, item.getExternalMediaKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "外部媒体引用已失效，请重新选择");
        }
        if (!StringUtils.hasText(item.getMediaType()) || "OTHER".equals(item.getMediaType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持添加图片、视频或音频文件");
        }
        return item;
    }

    private Album getAlbumOrThrow(Long albumId) {
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.ALBUM_NOT_FOUND);
        }
        return album;
    }

    private void checkOwner(Album album, Long userId) {
        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED);
        }
    }

    private void checkAccess(Album album, Long userId) {
        if (album.getUserId().equals(userId)) {
            return;
        }
        if ("PUBLIC".equals(album.getVisibility())) {
            return;
        }
        if ("DEVICE_ONLY".equals(album.getVisibility())) {
            throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED, "设备专属相册仅允许绑定设备访问");
        }
        throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED);
    }

    private AlbumResponse toResponse(Album album) {
        AlbumResponse resp = new AlbumResponse();
        resp.setId(album.getId());
        resp.setUserId(album.getUserId());
        resp.setTitle(album.getTitle());
        resp.setDescription(album.getDescription());
        resp.setCoverUrl(buildCoverUrl(album));
        resp.setCoverMediaId(album.getCoverMediaId());
        resp.setCoverSourceId(album.getCoverSourceId());
        resp.setCoverSourceType(album.getCoverSourceType());
        resp.setCoverSourceName(album.getCoverSourceName());
        resp.setCoverExternalMediaKey(album.getCoverExternalMediaKey());
        resp.setCoverPath(album.getCoverPath());
        resp.setCoverFileName(album.getCoverFileName());
        resp.setCoverContentType(album.getCoverContentType());
        resp.setCoverMediaType(album.getCoverMediaType());
        resp.setBgmUrl(album.getBgmUrl());
        resp.setBgmVolume(album.getBgmVolume());
        resp.setVisibility(album.getVisibility());
        resp.setStatus(album.getStatus());
        resp.setSortOrder(album.getSortOrder());
        resp.setCreatedAt(album.getCreatedAt());
        resp.setUpdatedAt(album.getUpdatedAt());
        return resp;
    }

    private AlbumContentResponse toContentResponse(AlbumMedia albumMedia, Media media) {
        AlbumContentResponse response = new AlbumContentResponse();
        response.setId(albumMedia.getId());
        response.setMediaId(albumMedia.getMediaId());
        response.setExternalMediaKey(albumMedia.getExternalMediaKey());
        response.setSourceId(albumMedia.getSourceId());
        response.setSourceType(albumMedia.getSourceType());
        response.setSourceName(albumMedia.getSourceName());
        response.setPath(albumMedia.getFilePath());
        response.setSortOrder(albumMedia.getSortOrder());
        response.setDuration(albumMedia.getDuration());

        if (media != null) {
            response.setFileName(media.getFileName());
            response.setContentType(media.getContentType());
            response.setMediaType(media.getMediaType());
            response.setSourceId(firstNonNull(media.getSourceId(), albumMedia.getSourceId()));
            response.setSourceType(media.getSourceType());
            response.setSourceName(media.getSourceName());
            response.setPath(albumMedia.getFilePath());
            response.setUrl(buildContentUrl(media.getId()));
            response.setThumbnailUrl(buildThumbnailUrl(media.getId(), media.getThumbnailKey()));
            return response;
        }

        response.setFileName(albumMedia.getFileName());
        response.setContentType(albumMedia.getContentType());
        response.setMediaType(albumMedia.getMediaType());
        response.setUrl(buildExternalContentUrl(albumMedia.getSourceId(), albumMedia.getFilePath()));
        response.setThumbnailUrl(buildExternalThumbnailUrl(albumMedia.getSourceId(), albumMedia.getFilePath(), albumMedia.getMediaType()));
        return response;
    }

    private String buildCoverUrl(Album album) {
        if (hasExternalCover(album)) {
            return buildAlbumCoverUrl(album.getId());
        }
        Media coverMedia = resolveCoverMedia(album);
        if (coverMedia != null) {
            return buildAlbumCoverUrl(album.getId());
        }
        if (!StringUtils.hasText(album.getCoverUrl())) {
            return null;
        }
        if (StringUtils.hasText(resolveCoverObjectKey(album.getCoverUrl()))) {
            return buildAlbumCoverUrl(album.getId());
        }
        return album.getCoverUrl();
    }

    private String buildAlbumCoverUrl(Long albumId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/albums/{id}/cover")
                .buildAndExpand(albumId)
                .toUriString();
    }

    private Media resolveCoverMedia(Album album) {
        if (album.getCoverMediaId() == null) {
            return null;
        }
        Media media = mediaMapper.selectById(album.getCoverMediaId());
        if (media == null || !Objects.equals(media.getUserId(), album.getUserId())) {
            return null;
        }
        return media;
    }

    private Media getOwnedReadyMedia(Long mediaId, Long userId) {
        Media media = mediaMapper.selectById(mediaId);
        if (media == null) {
            throw new BusinessException(ResultCode.MEDIA_NOT_FOUND);
        }
        if (!Objects.equals(media.getUserId(), userId)) {
            throw new BusinessException(ResultCode.MEDIA_ACCESS_DENIED);
        }
        if (!MediaStatus.READY.name().equals(media.getStatus())) {
            throw new BusinessException(ResultCode.MEDIA_NOT_READY);
        }
        return media;
    }

    private boolean isCoverEligible(Media media) {
        return media != null && ("IMAGE".equals(media.getMediaType()) || "VIDEO".equals(media.getMediaType()));
    }

    private boolean isExternalCoverEligible(MediaSourceBrowseItemResponse item) {
        return item != null && "IMAGE".equals(item.getMediaType());
    }

    private boolean shouldUseThumbnailForCover(Media media) {
        return media != null && StringUtils.hasText(media.getThumbnailKey());
    }

    private boolean shouldUseExternalThumbnailForCover(Album album) {
        return "IMAGE".equals(album.getCoverMediaType());
    }

    private boolean hasExternalCover(Album album) {
        return album.getCoverSourceId() != null && StringUtils.hasText(album.getCoverPath());
    }

    private String buildLegacyCoverStorageValue(Media media) {
        if (shouldUseThumbnailForCover(media)) {
            return media.getThumbnailKey();
        }
        return media.getObjectKey();
    }

    private String resolveCoverObjectKey(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return null;
        }
        if (!coverUrl.contains("://") && !coverUrl.startsWith("/")) {
            return coverUrl;
        }
        try {
            URI uri = URI.create(coverUrl);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            String bucketPrefix = bucket + "/";
            if (normalizedPath.startsWith(bucketPrefix)) {
                return normalizedPath.substring(bucketPrefix.length());
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<Long, Media> loadInternalMediaMap(List<AlbumMedia> albumMediaList) {
        List<Long> mediaIds = albumMediaList.stream()
                .map(AlbumMedia::getMediaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (mediaIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return mediaMapper.selectBatchIds(mediaIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Media::getId, Function.identity(), (left, right) -> left));
    }

    private String buildContentUrl(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/media/{id}/content")
                .buildAndExpand(mediaId)
                .toUriString();
    }

    private String buildThumbnailUrl(Long mediaId, String thumbnailKey) {
        if (mediaId == null || !StringUtils.hasText(thumbnailKey)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/media/{id}/thumbnail")
                .buildAndExpand(mediaId)
                .toUriString();
    }

    private String buildExternalContentUrl(Long sourceId, String path) {
        if (sourceId == null || !StringUtils.hasText(path)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/media-sources/{id}/content")
                .queryParam("path", path)
                .buildAndExpand(sourceId)
                .toUriString();
    }

    private String buildExternalThumbnailUrl(Long sourceId, String path, String mediaType) {
        if (sourceId == null || !StringUtils.hasText(path) || !"IMAGE".equals(mediaType)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/media-sources/{id}/thumbnail")
                .queryParam("path", path)
                .buildAndExpand(sourceId)
                .toUriString();
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

    private Integer defaultSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private Integer defaultDuration(Integer duration) {
        return duration == null ? 5 : duration;
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }
}
