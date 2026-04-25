package com.cloudalbum.publisher.album.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudalbum.publisher.album.dto.*;
import com.cloudalbum.publisher.album.entity.Album;
import com.cloudalbum.publisher.album.entity.AlbumBgm;
import com.cloudalbum.publisher.album.entity.AlbumMedia;
import com.cloudalbum.publisher.album.mapper.AlbumBgmMapper;
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

    private static final Set<String> SUPPORTED_TRANSITION_STYLES = Set.of(
            "NONE", "FADE", "SLIDE", "CUBE", "REVEAL", "FLIP", "RANDOM");

    private final AlbumMapper albumMapper;
    private final AlbumBgmMapper albumBgmMapper;
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
        album.setTransitionStyle(normalizeTransitionStyle(request.getTransitionStyle()));
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
        if (request.getTransitionStyle() != null) album.setTransitionStyle(normalizeTransitionStyle(request.getTransitionStyle()));
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
        albumBgmMapper.delete(new LambdaQueryWrapper<AlbumBgm>()
                .eq(AlbumBgm::getAlbumId, albumId));
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
    @Transactional
    public AlbumResponse updateBgm(Long albumId, Long userId, AlbumBgmRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        if (request.getBgmVolume() != null) {
            album.setBgmVolume(request.getBgmVolume());
            albumMapper.updateById(album);
        }
        if (Boolean.TRUE.equals(request.getClear())) {
            albumBgmMapper.delete(new LambdaQueryWrapper<AlbumBgm>()
                    .eq(AlbumBgm::getAlbumId, albumId));
            syncLegacyBgm(albumId);
        } else if (request.getMediaId() != null
                || request.getSourceId() != null
                || StringUtils.hasText(request.getExternalMediaKey())
                || StringUtils.hasText(request.getPath())) {
            albumBgmMapper.delete(new LambdaQueryWrapper<AlbumBgm>()
                    .eq(AlbumBgm::getAlbumId, albumId));
            addBgm(albumId, userId, request);
        }
        return toResponse(albumMapper.selectById(albumId));
    }

    @Override
    public List<AlbumBgmItemResponse> listBgms(Long albumId, Long userId) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        return loadAlbumBgms(albumId).stream().map(this::toBgmItemResponse).toList();
    }

    @Override
    @Transactional
    public AlbumBgmItemResponse addBgm(Long albumId, Long userId, AlbumBgmRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        AlbumBgm bgm = buildAlbumBgm(albumId, userId, request, null);
        bgm.setSortOrder(nextBgmSortOrder(albumId));
        albumBgmMapper.insert(bgm);
        syncLegacyBgm(albumId);
        return toBgmItemResponse(bgm);
    }


    @Override
    @Transactional
    public List<AlbumBgmItemResponse> addBgms(Long albumId, Long userId, List<AlbumBgmRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "待添加BGM不能为空");
        }
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        Set<String> requestKeys = new HashSet<>();
        List<AlbumBgmItemResponse> responses = new ArrayList<>();
        for (AlbumBgmRequest request : requests) {
            String uniqueKey = StringUtils.hasText(request.getExternalMediaKey())
                    ? "external:" + request.getExternalMediaKey()
                    : "internal:" + request.getMediaId();
            if (!requestKeys.add(uniqueKey)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "批量添加中存在重复BGM");
            }
            AlbumBgm bgm = buildAlbumBgm(albumId, userId, request, null);
            bgm.setSortOrder(nextBgmSortOrder(albumId) + responses.size());
            albumBgmMapper.insert(bgm);
            responses.add(toBgmItemResponse(bgm));
        }
        syncLegacyBgm(albumId);
        return responses;
    }

    @Override
    @Transactional
    public AlbumBgmItemResponse updateBgmItem(Long albumId, Long bgmId, Long userId, AlbumBgmRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        AlbumBgm existing = getAlbumBgmOrThrow(albumId, bgmId);
        AlbumBgm replacement = buildAlbumBgm(albumId, userId, request, existing.getId());
        existing.setMediaId(replacement.getMediaId());
        existing.setSourceId(replacement.getSourceId());
        existing.setSourceType(replacement.getSourceType());
        existing.setSourceName(replacement.getSourceName());
        existing.setExternalMediaKey(replacement.getExternalMediaKey());
        existing.setFilePath(replacement.getFilePath());
        existing.setFileName(replacement.getFileName());
        existing.setContentType(replacement.getContentType());
        existing.setMediaType(replacement.getMediaType());
        albumBgmMapper.updateById(existing);
        syncLegacyBgm(albumId);
        return toBgmItemResponse(existing);
    }

    @Override
    @Transactional
    public void removeBgm(Long albumId, Long bgmId, Long userId) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        AlbumBgm existing = getAlbumBgmOrThrow(albumId, bgmId);
        albumBgmMapper.deleteById(existing.getId());
        normalizeBgmSortOrders(albumId);
        syncLegacyBgm(albumId);
    }
    @Override
    @Transactional
    public void reorderBgms(Long albumId, Long userId, AlbumBgmOrderRequest request) {
        Album album = getAlbumOrThrow(albumId);
        checkOwner(album, userId);
        List<AlbumBgm> bgms = loadAlbumBgms(albumId);
        if (bgms.size() != request.getIds().size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "排序列表不完整");
        }
        Set<Long> currentIds = bgms.stream().map(AlbumBgm::getId).collect(Collectors.toSet());
        if (!currentIds.equals(new LinkedHashSet<>(request.getIds()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "排序列表与当前BGM不匹配");
        }
        for (int i = 0; i < request.getIds().size(); i += 1) {
            AlbumBgm update = new AlbumBgm();
            update.setId(request.getIds().get(i));
            update.setSortOrder(i);
            albumBgmMapper.updateById(update);
        }
        syncLegacyBgm(albumId);
    }

    private Album getAlbumOrThrow(Long albumId) {
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.ALBUM_NOT_FOUND);
        }
        return album;
    }

    private void checkOwner(Album album, Long userId) {
        if (!Objects.equals(album.getUserId(), userId)) {
            throw new BusinessException(ResultCode.ALBUM_ACCESS_DENIED);
        }
    }

    private void checkAccess(Album album, Long userId) {
        if (Objects.equals(album.getUserId(), userId)) {
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
        resp.setBgmUrl(buildBgmUrl(album));
        resp.setBgmMediaId(album.getBgmMediaId());
        resp.setBgmSourceId(album.getBgmSourceId());
        resp.setBgmSourceType(album.getBgmSourceType());
        resp.setBgmSourceName(album.getBgmSourceName());
        resp.setBgmExternalMediaKey(album.getBgmExternalMediaKey());
        resp.setBgmPath(album.getBgmPath());
        resp.setBgmFileName(album.getBgmFileName());
        resp.setBgmContentType(album.getBgmContentType());
        resp.setBgmMediaType(album.getBgmMediaType());
        resp.setBgmVolume(album.getBgmVolume());
        resp.setTransitionStyle(resolveTransitionStyle(album.getTransitionStyle()));
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


    private AlbumBgm buildAlbumBgm(Long albumId, Long userId, AlbumBgmRequest request, Long ignoreId) {
        if (request.getSourceId() != null || StringUtils.hasText(request.getExternalMediaKey()) || StringUtils.hasText(request.getPath())) {
            MediaSourceBrowseItemResponse externalItem = resolveExternalMediaItem(
                    request.getSourceId(),
                    userId,
                    request.getPath(),
                    request.getExternalMediaKey());
            if (!isAudioMediaType(externalItem.getMediaType())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "BGM 仅支持音频");
            }
            ensureExternalBgmNotExists(albumId, externalItem.getExternalMediaKey(), ignoreId);
            AlbumBgm bgm = new AlbumBgm();
            bgm.setAlbumId(albumId);
            bgm.setSourceId(externalItem.getSourceId());
            bgm.setSourceType(externalItem.getSourceType());
            bgm.setSourceName(externalItem.getSourceName());
            bgm.setExternalMediaKey(externalItem.getExternalMediaKey());
            bgm.setFilePath(externalItem.getPath());
            bgm.setFileName(firstText(externalItem.getFileName(), externalItem.getName()));
            bgm.setContentType(externalItem.getContentType());
            bgm.setMediaType(externalItem.getMediaType());
            return bgm;
        }
        if (request.getMediaId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "BGM 媒体不能为空");
        }
        Media media = getOwnedReadyMedia(request.getMediaId(), userId);
        if (!isAudioMediaType(media.getMediaType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "BGM 仅支持音频");
        }
        ensureInternalBgmNotExists(albumId, media.getId(), ignoreId);
        AlbumBgm bgm = new AlbumBgm();
        bgm.setAlbumId(albumId);
        bgm.setMediaId(media.getId());
        bgm.setFileName(media.getFileName());
        bgm.setContentType(media.getContentType());
        bgm.setMediaType(media.getMediaType());
        return bgm;
    }

    private void ensureInternalBgmNotExists(Long albumId, Long mediaId, Long ignoreId) {
        LambdaQueryWrapper<AlbumBgm> query = new LambdaQueryWrapper<AlbumBgm>()
                .eq(AlbumBgm::getAlbumId, albumId)
                .eq(AlbumBgm::getMediaId, mediaId);
        if (ignoreId != null) {
            query.ne(AlbumBgm::getId, ignoreId);
        }
        if (albumBgmMapper.selectCount(query) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该音频已在 BGM 列表中");
        }
    }

    private void ensureExternalBgmNotExists(Long albumId, String externalMediaKey, Long ignoreId) {
        LambdaQueryWrapper<AlbumBgm> query = new LambdaQueryWrapper<AlbumBgm>()
                .eq(AlbumBgm::getAlbumId, albumId)
                .eq(AlbumBgm::getExternalMediaKey, externalMediaKey);
        if (ignoreId != null) {
            query.ne(AlbumBgm::getId, ignoreId);
        }
        if (albumBgmMapper.selectCount(query) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该音频已在 BGM 列表中");
        }
    }

    private List<AlbumBgm> loadAlbumBgms(Long albumId) {
        return albumBgmMapper.selectList(new LambdaQueryWrapper<AlbumBgm>()
                .eq(AlbumBgm::getAlbumId, albumId)
                .orderByAsc(AlbumBgm::getSortOrder)
                .orderByAsc(AlbumBgm::getId));
    }

    private AlbumBgm getFirstAlbumBgm(Long albumId) {
        return loadAlbumBgms(albumId).stream().findFirst().orElse(null);
    }

    private AlbumBgm getAlbumBgmOrThrow(Long albumId, Long bgmId) {
        AlbumBgm bgm = albumBgmMapper.selectById(bgmId);
        if (bgm == null || !Objects.equals(bgm.getAlbumId(), albumId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "BGM 不存在");
        }
        return bgm;
    }

    private int nextBgmSortOrder(Long albumId) {
        return loadAlbumBgms(albumId).size();
    }

    private void normalizeBgmSortOrders(Long albumId) {
        List<AlbumBgm> bgms = loadAlbumBgms(albumId);
        for (int i = 0; i < bgms.size(); i += 1) {
            AlbumBgm update = new AlbumBgm();
            update.setId(bgms.get(i).getId());
            update.setSortOrder(i);
            albumBgmMapper.updateById(update);
        }
    }

    private void syncLegacyBgm(Long albumId) {
        AlbumBgm first = getFirstAlbumBgm(albumId);
        LambdaUpdateWrapper<Album> update = new LambdaUpdateWrapper<Album>()
                .eq(Album::getId, albumId)
                .set(Album::getBgmMediaId, first != null ? first.getMediaId() : null)
                .set(Album::getBgmSourceId, first != null ? first.getSourceId() : null)
                .set(Album::getBgmSourceType, first != null ? first.getSourceType() : null)
                .set(Album::getBgmSourceName, first != null ? first.getSourceName() : null)
                .set(Album::getBgmExternalMediaKey, first != null ? first.getExternalMediaKey() : null)
                .set(Album::getBgmPath, first != null ? first.getFilePath() : null)
                .set(Album::getBgmFileName, first != null ? first.getFileName() : null)
                .set(Album::getBgmContentType, first != null ? first.getContentType() : null)
                .set(Album::getBgmMediaType, first != null ? first.getMediaType() : null)
                .set(Album::getBgmUrl, first != null ? toBgmItemResponse(first).getUrl() : null);
        albumMapper.update(null, update);
    }

    private void writeAlbumBgmItem(AlbumBgm bgm, Long userId, HttpServletRequest request, HttpServletResponse response) {
        if (bgm.isExternal()) {
            resolveExternalMediaItem(bgm.getSourceId(), userId, bgm.getFilePath(), bgm.getExternalMediaKey());
            mediaSourceService.writeMediaContent(bgm.getSourceId(), userId, bgm.getFilePath(), false, request, response);
            return;
        }
        Media media = getOwnedReadyMedia(bgm.getMediaId(), userId);
        mediaHttpWriter.write(
                objectStorageMediaContentResolver.resolve(media, false),
                request,
                response,
                "读取相册BGM失败");
    }

    private AlbumBgmItemResponse toBgmItemResponse(AlbumBgm bgm) {
        AlbumBgmItemResponse response = new AlbumBgmItemResponse();
        response.setId(bgm.getId());
        response.setMediaId(bgm.getMediaId());
        response.setExternalMediaKey(bgm.getExternalMediaKey());
        response.setSourceId(bgm.getSourceId());
        response.setSourceType(bgm.getSourceType());
        response.setSourceName(bgm.getSourceName());
        response.setPath(bgm.getFilePath());
        response.setFileName(bgm.getFileName());
        response.setContentType(bgm.getContentType());
        response.setMediaType(bgm.getMediaType());
        response.setSortOrder(bgm.getSortOrder());
        response.setUrl(bgm.getMediaId() != null ? buildContentUrl(bgm.getMediaId()) : buildExternalContentUrl(bgm.getSourceId(), bgm.getFilePath()));
        return response;
    }


    @Override
    public void writeAlbumBgm(Long albumId, Long userId, HttpServletRequest request, HttpServletResponse response) {
        Album album = getAlbumOrThrow(albumId);
        checkAccess(album, userId);
        AlbumBgm firstBgm = getFirstAlbumBgm(albumId);
        if (firstBgm != null) {
            writeAlbumBgmItem(firstBgm, album.getUserId(), request, response);
            return;
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "相册BGM不存在");
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

    private String buildBgmUrl(Album album) {
        AlbumBgm firstBgm = getFirstAlbumBgm(album.getId());
        if (firstBgm != null) {
            return toBgmItemResponse(firstBgm).getUrl();
        }
        return StringUtils.hasText(album.getBgmUrl()) ? album.getBgmUrl() : null;
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

    private boolean isAudioMediaType(String mediaType) {
        return "AUDIO".equals(mediaType);
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

    private String normalizeTransitionStyle(String transitionStyle) {
        if (!StringUtils.hasText(transitionStyle)) {
            return "NONE";
        }
        String normalized = transitionStyle.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TRANSITION_STYLES.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的播放转场样式");
        }
        return normalized;
    }

    private String resolveTransitionStyle(String transitionStyle) {
        return StringUtils.hasText(transitionStyle) ? transitionStyle : "NONE";
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

    private <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }

}
