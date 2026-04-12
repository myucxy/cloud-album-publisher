package com.cloudalbum.publisher.auth.filter;

import com.cloudalbum.publisher.common.constant.CacheConstants;
import com.cloudalbum.publisher.common.constant.SecurityConstants;
import com.cloudalbum.publisher.common.security.DeviceAuthPrincipal;
import com.cloudalbum.publisher.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            processToken(token);
        }
        filterChain.doFilter(request, response);
    }

    private void processToken(String token) {
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            return;
        }
        String tokenType = claims.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class);
        if (SecurityConstants.TOKEN_TYPE_ACCESS.equals(tokenType)) {
            processUserAccessToken(claims);
            return;
        }
        if (SecurityConstants.TOKEN_TYPE_DEVICE_ACCESS.equals(tokenType)) {
            processDeviceAccessToken(claims);
        }
    }

    private void processUserAccessToken(Claims claims) {
        String jti = claims.getId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(CacheConstants.TOKEN_BLACKLIST_KEY + jti))) {
            log.debug("Token is blacklisted: {}", jti);
            return;
        }

        Long userId = claims.get(SecurityConstants.CLAIM_USER_ID, Long.class);
        List<?> rawRoles = claims.get(SecurityConstants.CLAIM_ROLES, List.class);
        List<SimpleGrantedAuthority> authorities = rawRoles == null ? List.of()
                : rawRoles.stream()
                .map(r -> new SimpleGrantedAuthority(r.toString()))
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void processDeviceAccessToken(Claims claims) {
        Long userId = claims.get(SecurityConstants.CLAIM_USER_ID, Long.class);
        Long deviceId = claims.get(SecurityConstants.CLAIM_DEVICE_ID, Long.class);
        String deviceUid = claims.get(SecurityConstants.CLAIM_DEVICE_UID, String.class);
        if (userId == null || deviceId == null || !StringUtils.hasText(deviceUid)) {
            return;
        }

        DeviceAuthPrincipal principal = new DeviceAuthPrincipal(userId, deviceId, deviceUid);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return header.substring(SecurityConstants.BEARER_PREFIX.length());
        }
        return null;
    }
}
