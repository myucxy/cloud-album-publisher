package com.cloudalbum.publisher.media.service;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.media.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {

    PageResult<MediaResponse> listMedia(Long userId,
                                        PageRequest pageRequest,
                                        String status,
                                        String mediaType,
                                        String sourceType,
                                        Long sourceId,
                                        String folderPath,
                                        String keyword);

    MediaLibraryGroupsResponse getMediaGroups(Long userId, String keyword);

    MediaUploadInitResponse initUpload(Long userId, MediaUploadInitRequest request);

    MediaUploadPartResponse uploadPart(Long userId, String uploadId, Integer partNumber, MultipartFile file);

    MediaResponse completeUpload(Long userId, String uploadId, MediaUploadCompleteRequest request);

    MediaUploadStatusResponse getUploadStatus(Long userId, String uploadId);

    MediaResponse getMedia(Long mediaId, Long userId);

    void deleteMedia(Long mediaId, Long userId);

    void triggerProcess(Long mediaId, Long userId);

    MediaStatusResponse getMediaStatus(Long mediaId, Long userId);

    void writeMediaContent(Long mediaId, Long userId, boolean thumbnail, HttpServletRequest request, HttpServletResponse response);
}
