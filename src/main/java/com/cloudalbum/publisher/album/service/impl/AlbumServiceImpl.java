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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlbumServiceImpl implements AlbumService {

    private final AlbumMapper albumMapper;
    private final AlbumMediaMapper albumMediaMapper;
    private final MediaMapper mediaMapper;
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
                .map(this::toResponse).collect(Collectors.toList());
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
    public PageResult<AlbumMedia> listContents(Long albumId, Long userId, PageRequest pageRequest) {
        Album album = getAlbumOrThrow(albumId);
        checkAccess(album, userId);
        IPage<AlbumMedia> page = albumMediaMapper.selectPage(
                new Page<>(pageRequest.getPage(), pageRequest.getSize()),
                new LambdaQueryWrapper<AlbumMedia>()
                        .eq(AlbumMedia::getAlbumId, albumId)
                        .orderByAsc(AlbumMedia::getSortOrder));
        return PageResult.of(page);
    }

    @Override
    public AlbumMedia addContent(Long albumId, Long userId, AlbumAddContentRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        Media media = mediaMapper.selectById(request.getMediaId());
        if (media == null) {
            throw new BusinessException(ResultCode.MEDIA_NOT_FOUND);
        }
        if (!media.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.MEDIA_ACCESS_DENIED);
        }
        if (!MediaStatus.READY.name().equals(media.getStatus())) {
            throw new BusinessException(ResultCode.MEDIA_NOT_READY);
        }
        if (albumMediaMapper.selectCount(new LambdaQueryWrapper<AlbumMedia>()
                .eq(AlbumMedia::getAlbumId, albumId)
                .eq(AlbumMedia::getMediaId, request.getMediaId())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该媒体已在相册中");
        }
        AlbumMedia am = new AlbumMedia();
        am.setAlbumId(albumId);
        am.setMediaId(request.getMediaId());
        am.setSortOrder(request.getSortOrder());
        am.setDuration(request.getDuration());
        albumMediaMapper.insert(am);
        return am;
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
    public AlbumResponse updateCover(Long albumId, Long userId, AlbumCoverRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        Media media = getOwnedReadyMedia(request.getMediaId(), userId);
        if (!isCoverEligible(media)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "封面媒体仅支持图片或视频");
        }
        if ("VIDEO".equals(media.getMediaType()) && !StringUtils.hasText(media.getThumbnailKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前视频暂无可用缩略图");
        }
        album.setCoverMediaId(media.getId());
        album.setCoverUrl(buildLegacyCoverStorageValue(media));
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
        resp.setBgmUrl(album.getBgmUrl());
        resp.setBgmVolume(album.getBgmVolume());
        resp.setVisibility(album.getVisibility());
        resp.setStatus(album.getStatus());
        resp.setSortOrder(album.getSortOrder());
        resp.setCreatedAt(album.getCreatedAt());
        resp.setUpdatedAt(album.getUpdatedAt());
        return resp;
    }

    private String buildCoverUrl(Album album) {
        Media coverMedia = resolveCoverMedia(album);
        if (coverMedia != null) {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/albums/{id}/cover")
                    .buildAndExpand(album.getId())
                    .toUriString();
        }
        if (!StringUtils.hasText(album.getCoverUrl())) {
            return null;
        }
        if (StringUtils.hasText(resolveCoverObjectKey(album.getCoverUrl()))) {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/albums/{id}/cover")
                    .buildAndExpand(album.getId())
                    .toUriString();
        }
        return album.getCoverUrl();
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

    private boolean shouldUseThumbnailForCover(Media media) {
        return media != null && StringUtils.hasText(media.getThumbnailKey());
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
}
