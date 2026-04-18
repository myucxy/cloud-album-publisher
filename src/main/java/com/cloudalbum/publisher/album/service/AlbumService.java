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

    AlbumResponse updateCover(Long albumId, Long userId, AlbumCoverRequest request);

    AlbumResponse updateBgm(Long albumId, Long userId, AlbumBgmRequest request);

    void writeAlbumCover(Long albumId, Long userId, HttpServletRequest request, HttpServletResponse response);

    void writeAlbumBgm(Long albumId, Long userId, HttpServletRequest request, HttpServletResponse response);
}
