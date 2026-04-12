package com.cloudalbum.publisher.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudalbum.publisher.audit.dto.AuditLogQueryRequest;
import com.cloudalbum.publisher.audit.dto.AuditLogResponse;
import com.cloudalbum.publisher.audit.entity.AuditLog;
import com.cloudalbum.publisher.audit.mapper.AuditLogMapper;
import com.cloudalbum.publisher.audit.service.AuditLogService;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Async
    @Override
    public void record(Long userId, String username, String action,
                       String resourceType, String resourceId,
                       String detail, String ip, String userAgent, String result) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setDetail(detail);
            auditLog.setIp(ip);
            auditLog.setUserAgent(userAgent);
            auditLog.setResult(result);
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 审计日志写入失败不影响主业务
            log.warn("审计日志写入失败: action={}, userId={}, error={}", action, userId, e.getMessage());
        }
    }

    @Override
    public PageResult<AuditLogResponse> listAuditLogs(AuditLogQueryRequest query, PageRequest pageRequest) {
        Page<AuditLog> page = new Page<>(pageRequest.getPage(), pageRequest.getSize());
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getCreatedAt);

        if (query.getUserId() != null) {
            wrapper.eq(AuditLog::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getAction())) {
            wrapper.eq(AuditLog::getAction, query.getAction());
        }
        if (StringUtils.hasText(query.getResourceType())) {
            wrapper.eq(AuditLog::getResourceType, query.getResourceType());
        }
        if (StringUtils.hasText(query.getResult())) {
            wrapper.eq(AuditLog::getResult, query.getResult());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(AuditLog::getCreatedAt, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(AuditLog::getCreatedAt, query.getEndTime());
        }

        Page<AuditLog> result = auditLogMapper.selectPage(page, wrapper);
        return PageResult.of(result.convert(this::toResponse));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .detail(log.getDetail())
                .ip(log.getIp())
                .result(log.getResult())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
