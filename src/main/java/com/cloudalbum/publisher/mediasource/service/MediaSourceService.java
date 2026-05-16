package com.cloudalbum.publisher.mediasource.service;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.media.dto.MediaResponse;
import com.cloudalbum.publisher.mediasource.dto.ConnectionTestResponse;
import com.cloudalbum.publisher.mediasource.dto.ExternalMediaItemResponse;
import com.cloudalbum.publisher.mediasource.dto.ExternalMediaScanSummary;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceBrowseResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceCreateRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceImportRequest;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceResponse;
import com.cloudalbum.publisher.mediasource.dto.MediaSourceUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface MediaSourceService {

    List<MediaSourceResponse> listMediaSources(Long userId);

    MediaSourceResponse createMediaSource(Long userId, MediaSourceCreateRequest request);

    MediaSourceResponse updateMediaSource(Long mediaSourceId, Long userId, MediaSourceUpdateRequest request);

    void deleteMediaSource(Long mediaSourceId, Long userId);

    MediaSourceBrowseResponse browse(Long mediaSourceId, Long userId, String path);

    MediaSourceBrowseResponse browse(Long userId, MediaSourceBrowseRequest request);

    PageResult<ExternalMediaItemResponse> listExternalMedia(Long mediaSourceId,
                                                            Long userId,
                                                            PageRequest pageRequest,
                                                            String path,
                                                            String folderPath,
                                                            String mediaType,
                                                            String status,
                                                            Boolean coverOnly,
                                                            String keyword);

    List<ExternalMediaScanSummary> scanExternalMediaSummaries(Long userId, String keyword);

    ConnectionTestResponse testConnection(Long mediaSourceId, Long userId);

    void writeMediaContent(Long mediaSourceId,
                           Long userId,
                           String path,
                           boolean thumbnail,
                           HttpServletRequest request,
                           HttpServletResponse response);

    List<MediaResponse> importMedia(Long mediaSourceId, Long userId, MediaSourceImportRequest request);
}
