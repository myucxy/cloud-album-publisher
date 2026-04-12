package com.cloudalbum.publisher.review.service;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.review.dto.ReviewRecordResponse;
import com.cloudalbum.publisher.review.dto.ReviewRejectRequest;
import com.cloudalbum.publisher.review.dto.ReviewSettingResponse;
import com.cloudalbum.publisher.review.dto.ReviewSettingUpdateRequest;

public interface ReviewService {

    /**
     * 提交媒体审核（由 B 模块媒体上传完成后调用，或管理员手动触发）
     */
    ReviewRecordResponse submitReview(Long mediaId, Long userId);

    /**
     * 查询待审核列表（管理员）
     */
    PageResult<ReviewRecordResponse> listPendingReviews(PageRequest pageRequest);

    /**
     * 查询审核记录详情
     */
    ReviewRecordResponse getReview(Long id);

    /**
     * 查询指定媒体的最新审核记录
     */
    ReviewRecordResponse getReviewByMediaId(Long mediaId);

    /**
     * 审核通过（管理员）
     */
    ReviewRecordResponse approve(Long id, Long reviewerId);

    /**
     * 审核驳回（管理员）
     */
    ReviewRecordResponse reject(Long id, Long reviewerId, ReviewRejectRequest request);

    /**
     * 获取审核设置
     */
    ReviewSettingResponse getSettings();

    /**
     * 更新审核设置
     */
    ReviewSettingResponse updateSettings(ReviewSettingUpdateRequest request);
}
