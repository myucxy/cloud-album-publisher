package com.cloudalbum.publisher.audit.aspect;

import com.cloudalbum.publisher.audit.annotation.Audit;
import com.cloudalbum.publisher.audit.service.AuditLogService;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计日志 AOP 切面，拦截所有标注 @Audit 的方法，自动记录操作日志。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint pjp, Audit audit) throws Throwable {
        String ip = null;
        String userAgent = null;
        Long userId = null;
        String username = null;

        // 尝试获取请求信息
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ip = resolveClientIp(request);
                userAgent = request.getHeader("User-Agent");
            }
            userId = SecurityUtil.getCurrentUserId();
            username = SecurityUtil.getCurrentUsername();
        } catch (Exception ignored) {
            // 匿名操作或上下文获取失败，不阻断主流程
        }

        String resultStatus = "SUCCESS";
        try {
            Object ret = pjp.proceed();
            return ret;
        } catch (Throwable t) {
            resultStatus = "FAIL";
            throw t;
        } finally {
            auditLogService.record(
                    userId, username,
                    audit.action(), audit.resourceType(),
                    null, null, ip, userAgent, resultStatus);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
