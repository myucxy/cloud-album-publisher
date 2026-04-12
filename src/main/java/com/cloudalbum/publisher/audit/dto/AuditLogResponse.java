package com.cloudalbum.publisher.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "审计日志响应")
public class AuditLogResponse {

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "操作用户ID")
    private Long userId;

    @Schema(description = "操作用户名（快照）")
    private String username;

    @Schema(description = "操作类型")
    private String action;

    @Schema(description = "资源类型")
    private String resourceType;

    @Schema(description = "资源ID")
    private String resourceId;

    @Schema(description = "操作详情（JSON）")
    private String detail;

    @Schema(description = "客户端IP")
    private String ip;

    @Schema(description = "操作结果：SUCCESS / FAIL")
    private String result;

    @Schema(description = "操作时间")
    private LocalDateTime createdAt;
}
