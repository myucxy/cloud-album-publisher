package com.cloudalbum.publisher.album.service;

import com.cloudalbum.publisher.album.dto.*;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface AlbumService {

    PageResult<AlbumResponse> listAlbums(Long userId, PageRequest pageRequest, String visibility);

    AlbumResponse getAlbum(Long albumId, Long currentUserId);

    AlbumResponse createAlbum(Long userId, AlbumCreateRequest request);

    AlbumResponse updateAlbum(Long albumId, Long userId, AlbumUpdateRequest request);

    void deleteAlbum(Long albumId, Long userId);

    PageResult<AlbumContentResponse> listContents(Long albumId, Long userId, PageRequest pageRequest);

    AlbumContentResponse addContent(Long albumId, Long userId, AlbumAddContentRequest request);

    List<AlbumContentResponse> addContents(Long albumId, Long userId, List<AlbumAddContentRequest> requests);

    void removeContent(Long albumId, Long contentId, Long userId);

    void removeContents(Long albumId, Long userId, List<Long> contentIds);

    AlbumResponse updateCover(Long albumId, Long userId, AlbumCoverRequest request);

    AlbumResponse updateBgm(Long albumId, Long userId, AlbumBgmRequest request);

    List<AlbumBgmItemResponse> listBgms(Long albumId, Long userId);

    AlbumBgmItemResponse addBgm(Long albumId, Long userId, AlbumBgmRequest request);

    List<AlbumBgmItemResponse> addBgms(Long albumId, Long userId, List<AlbumBgmRequest> requests);

    AlbumBgmItemResponse updateBgmItem(Long albumId, Long bgmId, Long userId, AlbumBgmRequest request);

    void removeBgm(Long albumId, Long bgmId, Long userId);

    void reorderBgms(Long albumId, Long userId, AlbumBgmOrderRequest request);

    void writeAlbumCover(Long albumId, Long userId, HttpServletRequest request, HttpServletResponse response);

    void writeAlbumBgm(Long albumId, Long userId, HttpServletRequest request, HttpServletResponse response);
}
