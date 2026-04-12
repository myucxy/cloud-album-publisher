package com.cloudalbum.publisher.common.util;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.AuthException;
import com.cloudalbum.publisher.common.security.DeviceAuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public final class SecurityUtil {

    private SecurityUtil() {}

    /** 获取当前登录用户ID，未登录时抛 AuthException */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthException(ResultCode.UNAUTHORIZED);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        if (principal instanceof DeviceAuthPrincipal devicePrincipal) {
            return devicePrincipal.getUserId();
        }
        throw new AuthException(ResultCode.UNAUTHORIZED);
    }

    /** 获取当前设备认证主体，未使用设备凭证时抛 AuthException */
    public static DeviceAuthPrincipal getCurrentDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthException(ResultCode.UNAUTHORIZED);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof DeviceAuthPrincipal devicePrincipal) {
            return devicePrincipal;
        }
        throw new AuthException(ResultCode.UNAUTHORIZED, "当前请求未使用设备凭证");
    }

    /** 获取当前登录用户名 */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthException(ResultCode.UNAUTHORIZED);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) {
            return ud.getUsername();
        }
        return auth.getName();
    }

    /** 判断当前用户是否是管理员 */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
