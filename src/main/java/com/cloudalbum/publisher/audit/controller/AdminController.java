package com.cloudalbum.publisher.audit.controller;

import com.cloudalbum.publisher.audit.dto.AdminStatsResponse;
import com.cloudalbum.publisher.audit.dto.AuditLogQueryRequest;
import com.cloudalbum.publisher.audit.dto.AuditLogResponse;
import com.cloudalbum.publisher.audit.mapper.AuditLogMapper;
import com.cloudalbum.publisher.audit.service.AuditLogService;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.distribution.mapper.DistributionMapper;
import com.cloudalbum.publisher.review.mapper.ReviewRecordMapper;
import com.cloudalbum.publisher.user.mapper.UserMapper;
import com.cloudalbum.publisher.album.mapper.AlbumMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.distribution.entity.Distribution;
import com.cloudalbum.publisher.review.entity.ReviewRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端（管理员）")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditLogService auditLogService;
    private final AuditLogMapper auditLogMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final DistributionMapper distributionMapper;
    private final UserMapper userMapper;
    private final AlbumMapper albumMapper;

    @Operation(summary = "审计日志列表（分页、多条件筛选）")
    @GetMapping("/audit-logs")
    public Result<PageResult<AuditLogResponse>> listAuditLogs(
            AuditLogQueryRequest query,
            @Valid PageRequest pageRequest) {
        return Result.success(auditLogService.listAuditLogs(query, pageRequest));
    }

    @Operation(summary = "管理端统计数据")
    @GetMapping("/stats")
    public Result<AdminStatsResponse> getStats() {
        long pendingReviews = reviewRecordMapper.selectCount(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getStatus, "PENDING"));

        long activeDistributions = distributionMapper.selectCount(
                new LambdaQueryWrapper<Distribution>()
                        .eq(Distribution::getStatus, "ACTIVE"));

        // 用户总数和相册总数依赖 A 模块的 Mapper，此处用审计日志表计数做占位，
        // 联调时由 A 提供 UserMapper / AlbumMapper 后替换。
        AdminStatsResponse stats = AdminStatsResponse.builder()
                .totalUsers(userMapper.selectCount(null))
                .totalAlbums(albumMapper.selectCount(null))
                .pendingReviews(pendingReviews)
                .activeDistributions(activeDistributions)
                .build();

        return Result.success(stats);
    }
}
