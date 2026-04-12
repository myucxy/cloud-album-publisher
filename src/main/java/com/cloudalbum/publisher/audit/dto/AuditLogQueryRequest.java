package com.cloudalbum.publisher.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "审计日志查询请求")
public class AuditLogQueryRequest {

    @Schema(description = "操作用户ID")
    private Long userId;

    @Schema(description = "操作类型，如：USER_LOGIN")
    private String action;

    @Schema(description = "资源类型，如：album")
    private String resourceType;

    @Schema(description = "操作结果：SUCCESS / FAIL")
    private String result;

    @Schema(description = "查询起始时间（yyyy-MM-dd'T'HH:mm:ss）")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @Schema(description = "查询结束时间（yyyy-MM-dd'T'HH:mm:ss）")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
