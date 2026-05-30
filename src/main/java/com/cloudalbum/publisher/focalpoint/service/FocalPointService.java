package com.cloudalbum.publisher.focalpoint.service;

import com.cloudalbum.publisher.focalpoint.dto.*;

import java.util.List;

public interface FocalPointService {

    void updateAlbumSettings(Long albumId, Long userId, AlbumFocalPointSettingsRequest request);

    void updateFocalPoint(Long albumId, Long contentId, Long userId, FocalPointUpdateRequest request);

    void clearFocalPoint(Long albumId, Long contentId, Long userId);

    FocalPointProcessResult batchProcess(Long albumId, Long userId, FocalPointBatchProcessRequest request);

    void autoProcessIfEnabled(Long albumId, Long albumMediaId);

    List<String> getAvailableProviderTypes();

    List<VisionLlmConfigResponse> listLlmConfigs(Long userId);

    VisionLlmConfigResponse getLlmConfig(Long id, Long userId);

    VisionLlmConfigResponse createLlmConfig(Long userId, VisionLlmConfigRequest request);

    VisionLlmConfigResponse updateLlmConfig(Long id, Long userId, VisionLlmConfigRequest request);

    void deleteLlmConfig(Long id, Long userId);
}
