package com.cloudalbum.publisher.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.media.entity.Media;
import com.cloudalbum.publisher.media.mapper.MediaMapper;
import com.cloudalbum.publisher.review.dto.ReviewRecordResponse;
import com.cloudalbum.publisher.review.dto.ReviewRejectRequest;
import com.cloudalbum.publisher.review.dto.ReviewSettingResponse;
import com.cloudalbum.publisher.review.dto.ReviewSettingUpdateRequest;
import com.cloudalbum.publisher.review.entity.ReviewRecord;
import com.cloudalbum.publisher.review.entity.ReviewSetting;
import com.cloudalbum.publisher.review.mapper.ReviewRecordMapper;
import com.cloudalbum.publisher.review.mapper.ReviewSettingMapper;
import com.cloudalbum.publisher.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final long REVIEW_SETTING_ID = 1L;

    private final ReviewRecordMapper reviewRecordMapper;
    private final ReviewSettingMapper reviewSettingMapper;
    private final MediaMapper mediaMapper;

    @Override
    @Transactional
    public ReviewRecordResponse submitReview(Long mediaId, Long userId) {
        boolean autoApproveEnabled = isAutoApproveEnabled();
        ReviewRecord existing = reviewRecordMapper.selectOne(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getMediaId, mediaId)
                        .orderByDesc(ReviewRecord::getCreatedAt)
                        .last("LIMIT 1"));
        if (existing != null) {
            if ("PENDING".equals(existing.getStatus())) {
                if (autoApproveEnabled) {
                    LocalDateTime now = LocalDateTime.now();
                    existing.setStatus("APPROVED");
                    existing.setReviewedAt(now);
                    existing.setUpdatedAt(now);
                    reviewRecordMapper.updateById(existing);
                }
                return toResponse(existing);
            }
            if (autoApproveEnabled && "APPROVED".equals(existing.getStatus())) {
                return toResponse(existing);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ReviewRecord record = new ReviewRecord();
        record.setMediaId(mediaId);
        record.setUserId(userId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        if (autoApproveEnabled) {
            record.setStatus("APPROVED");
            record.setReviewedAt(now);
        } else {
            record.setStatus("PENDING");
        }

        reviewRecordMapper.insert(record);
        return toResponse(record);
    }

    @Override
    public PageResult<ReviewRecordResponse> listPendingReviews(PageRequest pageRequest) {
        Page<ReviewRecord> page = new Page<>(pageRequest.getPage(), pageRequest.getSize());
        Page<ReviewRecord> result = reviewRecordMapper.selectPage(page,
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getStatus, "PENDING")
                        .orderByAsc(ReviewRecord::getCreatedAt));
        return PageResult.of(result.convert(this::toResponse));
    }

    @Override
    public ReviewRecordResponse getReview(Long id) {
        ReviewRecord record = reviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.REVIEW_NOT_FOUND);
        }
        return toResponse(record);
    }

    @Override
    public ReviewRecordResponse getReviewByMediaId(Long mediaId) {
        ReviewRecord record = reviewRecordMapper.selectOne(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getMediaId, mediaId)
                        .orderByDesc(ReviewRecord::getCreatedAt)
                        .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException(ResultCode.REVIEW_NOT_FOUND);
        }
        return toResponse(record);
    }

    @Override
    @Transactional
    public ReviewRecordResponse approve(Long id, Long reviewerId) {
        ReviewRecord record = getAndCheckPending(id);
        record.setStatus("APPROVED");
        record.setReviewerId(reviewerId);
        record.setReviewedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        reviewRecordMapper.updateById(record);
        return toResponse(record);
    }

    @Override
    @Transactional
    public ReviewRecordResponse reject(Long id, Long reviewerId, ReviewRejectRequest request) {
        ReviewRecord record = getAndCheckPending(id);
        record.setStatus("REJECTED");
        record.setReviewerId(reviewerId);
        record.setRejectReason(request.getRejectReason());
        record.setReviewedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        reviewRecordMapper.updateById(record);
        return toResponse(record);
    }

    @Override
    public ReviewSettingResponse getSettings() {
        return toSettingResponse(getOrCreateSetting());
    }

    @Override
    @Transactional
    public ReviewSettingResponse updateSettings(ReviewSettingUpdateRequest request) {
        ReviewSetting setting = getOrCreateSetting();
        setting.setAutoApproveEnabled(Boolean.TRUE.equals(request.getAutoApproveEnabled()));
        setting.setUpdatedAt(LocalDateTime.now());
        reviewSettingMapper.updateById(setting);
        if (Boolean.TRUE.equals(setting.getAutoApproveEnabled())) {
            approveReadyMedia();
        }
        return toSettingResponse(setting);
    }

    private ReviewRecord getAndCheckPending(Long id) {
        ReviewRecord record = reviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.REVIEW_NOT_FOUND);
        }
        if (!"PENDING".equals(record.getStatus())) {
            throw new BusinessException(ResultCode.REVIEW_ALREADY_PROCESSED);
        }
        return record;
    }

    private boolean isAutoApproveEnabled() {
        return Boolean.TRUE.equals(getOrCreateSetting().getAutoApproveEnabled());
    }

    private ReviewSetting getOrCreateSetting() {
        ReviewSetting setting = reviewSettingMapper.selectById(REVIEW_SETTING_ID);
        if (setting != null) {
            return setting;
        }

        LocalDateTime now = LocalDateTime.now();
        ReviewSetting created = new ReviewSetting();
        created.setId(REVIEW_SETTING_ID);
        created.setAutoApproveEnabled(true);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        reviewSettingMapper.insert(created);
        return created;
    }

    private void approveReadyMedia() {
        LocalDateTime now = LocalDateTime.now();
        List<ReviewRecord> pendingRecords = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getStatus, "PENDING"));
        if (!CollectionUtils.isEmpty(pendingRecords)) {
            List<Long> mediaIds = pendingRecords.stream()
                    .map(ReviewRecord::getMediaId)
                    .distinct()
                    .toList();
            Map<Long, Media> readyMediaMap = mediaMapper.selectBatchIds(mediaIds).stream()
                    .filter(media -> "READY".equals(media.getStatus()))
                    .collect(Collectors.toMap(Media::getId, Function.identity()));
            for (ReviewRecord record : pendingRecords) {
                if (!readyMediaMap.containsKey(record.getMediaId())) {
                    continue;
                }
                record.setStatus("APPROVED");
                record.setReviewedAt(now);
                record.setUpdatedAt(now);
                record.setRejectReason(null);
                reviewRecordMapper.updateById(record);
            }
        }

        List<Media> readyMediaList = mediaMapper.selectList(
                new LambdaQueryWrapper<Media>()
                        .eq(Media::getStatus, "READY"));
        if (CollectionUtils.isEmpty(readyMediaList)) {
            return;
        }

        List<ReviewRecord> existingRecords = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<ReviewRecord>()
                        .in(ReviewRecord::getMediaId, readyMediaList.stream().map(Media::getId).toList()));
        Set<Long> reviewedMediaIds = existingRecords.stream()
                .map(ReviewRecord::getMediaId)
                .collect(Collectors.toCollection(HashSet::new));

        for (Media media : readyMediaList) {
            if (reviewedMediaIds.contains(media.getId())) {
                continue;
            }
            ReviewRecord record = new ReviewRecord();
            record.setMediaId(media.getId());
            record.setUserId(media.getUserId());
            record.setStatus("APPROVED");
            record.setReviewedAt(now);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            reviewRecordMapper.insert(record);
        }
    }

    private ReviewRecordResponse toResponse(ReviewRecord record) {
        return ReviewRecordResponse.builder()
                .id(record.getId())
                .mediaId(record.getMediaId())
                .userId(record.getUserId())
                .reviewerId(record.getReviewerId())
                .status(record.getStatus())
                .rejectReason(record.getRejectReason())
                .reviewedAt(record.getReviewedAt())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private ReviewSettingResponse toSettingResponse(ReviewSetting setting) {
        return ReviewSettingResponse.builder()
                .autoApproveEnabled(Boolean.TRUE.equals(setting.getAutoApproveEnabled()))
                .build();
    }
}
