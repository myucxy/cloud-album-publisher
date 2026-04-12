package com.cloudalbum.publisher.common.util;

import com.cloudalbum.publisher.common.constant.SecurityConstants;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;
    private final long deviceAccessTokenExpire;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expire}") long accessTokenExpire,
            @Value("${jwt.refresh-token-expire}") long refreshTokenExpire,
            @Value("${device.access-token-expire}") long deviceAccessTokenExpire) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpire = accessTokenExpire;
        this.refreshTokenExpire = refreshTokenExpire;
        this.deviceAccessTokenExpire = deviceAccessTokenExpire;
    }

    /** 生成 Access Token */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(SecurityConstants.CLAIM_USER_ID, userId)
                .claim(SecurityConstants.CLAIM_USERNAME, username)
                .claim(SecurityConstants.CLAIM_ROLES, roles)
                .claim(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpire * 1000))
                .signWith(secretKey)
                .compact();
    }

    /** 生成设备 Access Token */
    public String generateDeviceAccessToken(Long userId, Long deviceId, String deviceUid) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("device:" + deviceId)
                .claim(SecurityConstants.CLAIM_USER_ID, userId)
                .claim(SecurityConstants.CLAIM_DEVICE_ID, deviceId)
                .claim(SecurityConstants.CLAIM_DEVICE_UID, deviceUid)
                .claim(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_DEVICE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + deviceAccessTokenExpire * 1000))
                .signWith(secretKey)
                .compact();
    }

    /** 生成 Refresh Token */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(SecurityConstants.CLAIM_USER_ID, userId)
                .claim(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpire * 1000))
                .signWith(secretKey)
                .compact();
    }

    /** 解析 Token Claims，失败返回 null */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.debug("Token invalid: {}", e.getMessage());
            return null;
        }
    }

    /** 获取 Token 剩余有效秒数（用于黑名单TTL） */
    public long getRemainingSeconds(Claims claims) {
        long expMs = claims.getExpiration().getTime();
        long remaining = (expMs - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 0);
    }

    public long getAccessTokenExpire() {
        return accessTokenExpire;
    }

    public long getRefreshTokenExpire() {
        return refreshTokenExpire;
    }

    public long getDeviceAccessTokenExpire() {
        return deviceAccessTokenExpire;
    }
}
