package com.cloudalbum.publisher.audit.service;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.audit.dto.AuditLogResponse;
import com.cloudalbum.publisher.audit.dto.AuditLogQueryRequest;

public interface AuditLogService {

    /** 记录一条审计日志（由 AOP 切面或业务代码手动调用） */
    void record(Long userId, String username, String action,
                String resourceType, String resourceId,
                String detail, String ip, String userAgent, String result);

    /** 分页查询审计日志（管理员） */
    PageResult<AuditLogResponse> listAuditLogs(AuditLogQueryRequest query, PageRequest pageRequest);
}
