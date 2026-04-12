package com.cloudalbum.publisher.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 操作类型，如：USER_LOGIN / ALBUM_DELETE / DISTRIBUTION_ACTIVATE */
    private String action;

    /** 资源类型，如：album / media / distribution */
    private String resourceType;

    /** 资源ID */
    private String resourceId;

    /** 操作详情（JSON） */
    private String detail;

    private String ip;

    private String userAgent;

    /** 操作结果：SUCCESS / FAIL */
    private String result;

    private LocalDateTime createdAt;
}
