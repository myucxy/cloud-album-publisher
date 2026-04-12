package com.cloudalbum.publisher.review.controller;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import com.cloudalbum.publisher.review.dto.ReviewRecordResponse;
import com.cloudalbum.publisher.review.dto.ReviewRejectRequest;
import com.cloudalbum.publisher.review.dto.ReviewSettingResponse;
import com.cloudalbum.publisher.review.dto.ReviewSettingUpdateRequest;
import com.cloudalbum.publisher.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "内容审核管理（管理员）")
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "获取待审核列表（分页）")
    @GetMapping
    public Result<PageResult<ReviewRecordResponse>> listPendingReviews(@Valid PageRequest pageRequest) {
        return Result.success(reviewService.listPendingReviews(pageRequest));
    }

    @Operation(summary = "获取审核设置")
    @GetMapping("/settings")
    public Result<ReviewSettingResponse> getSettings() {
        return Result.success(reviewService.getSettings());
    }

    @Operation(summary = "更新审核设置")
    @PutMapping("/settings")
    public Result<ReviewSettingResponse> updateSettings(@Valid @RequestBody ReviewSettingUpdateRequest request) {
        return Result.success(reviewService.updateSettings(request));
    }

    @Operation(summary = "获取审核记录详情")
    @GetMapping("/{id}")
    public Result<ReviewRecordResponse> getReview(@PathVariable Long id) {
        return Result.success(reviewService.getReview(id));
    }

    @Operation(summary = "查询媒体的最新审核状态")
    @GetMapping("/media/{mediaId}")
    public Result<ReviewRecordResponse> getReviewByMedia(@PathVariable Long mediaId) {
        return Result.success(reviewService.getReviewByMediaId(mediaId));
    }

    @Operation(summary = "审核通过")
    @PostMapping("/{id}/approve")
    public Result<ReviewRecordResponse> approve(@PathVariable Long id) {
        return Result.success(reviewService.approve(id, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "审核驳回")
    @PostMapping("/{id}/reject")
    public Result<ReviewRecordResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody ReviewRejectRequest request) {
        return Result.success(reviewService.reject(id, SecurityUtil.getCurrentUserId(), request));
    }
}
