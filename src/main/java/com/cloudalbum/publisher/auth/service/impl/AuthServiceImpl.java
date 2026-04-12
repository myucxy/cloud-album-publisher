package com.cloudalbum.publisher.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.auth.dto.ChangePasswordRequest;
import com.cloudalbum.publisher.auth.dto.LoginRequest;
import com.cloudalbum.publisher.auth.dto.RefreshRequest;
import com.cloudalbum.publisher.auth.dto.RegisterRequest;
import com.cloudalbum.publisher.auth.dto.TokenResponse;
import com.cloudalbum.publisher.auth.service.AuthService;
import com.cloudalbum.publisher.common.constant.CacheConstants;
import com.cloudalbum.publisher.common.constant.SecurityConstants;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.AuthException;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.util.JwtUtil;
import com.cloudalbum.publisher.user.entity.User;
import com.cloudalbum.publisher.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        // 校验用户名唯一
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) > 0) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }
        // 校验邮箱唯一
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, request.getEmail())) > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname() : request.getUsername());
        user.setStatus(1);
        userMapper.insert(user);

        // 默认角色 ROLE_USER
        userMapper.insertUserRole(user.getId(), "ROLE_USER");

        return buildTokenResponse(user, List.of("ROLE_USER"));
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() != 1) {
            throw new AuthException(ResultCode.ACCOUNT_DISABLED);
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        return buildTokenResponse(user, roles);
    }

    @Override
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = jwtUtil.parseToken(request.getRefreshToken());
        if (claims == null) {
            throw new AuthException(ResultCode.TOKEN_INVALID);
        }
        String tokenType = claims.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class);
        if (!SecurityConstants.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new AuthException(ResultCode.TOKEN_INVALID);
        }

        Long userId = claims.get(SecurityConstants.CLAIM_USER_ID, Long.class);
        // 校验 Redis 中是否存在
        String storedToken = (String) redisTemplate.opsForValue()
                .get(CacheConstants.REFRESH_TOKEN_KEY + userId);
        if (!request.getRefreshToken().equals(storedToken)) {
            throw new AuthException(ResultCode.TOKEN_INVALID);
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new AuthException(ResultCode.ACCOUNT_DISABLED);
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(userId);
        return buildTokenResponse(user, roles);
    }

    @Override
    public void logout(String accessToken) {
        if (!StringUtils.hasText(accessToken)) return;
        String token = accessToken.startsWith(SecurityConstants.BEARER_PREFIX)
                ? accessToken.substring(SecurityConstants.BEARER_PREFIX.length())
                : accessToken;

        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) return;

        Long userId = claims.get(SecurityConstants.CLAIM_USER_ID, Long.class);
        // 加入黑名单，TTL = 剩余有效时间
        long ttl = jwtUtil.getRemainingSeconds(claims);
        if (ttl > 0) {
            redisTemplate.opsForValue().set(
                    CacheConstants.TOKEN_BLACKLIST_KEY + claims.getId(),
                    "1",
                    ttl,
                    TimeUnit.SECONDS);
        }
        // 删除 Refresh Token
        redisTemplate.delete(CacheConstants.REFRESH_TOKEN_KEY + userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_INCORRECT);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        redisTemplate.delete(CacheConstants.REFRESH_TOKEN_KEY + userId);
    }

    private TokenResponse buildTokenResponse(User user, List<String> roles) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        redisTemplate.opsForValue().set(
                CacheConstants.REFRESH_TOKEN_KEY + user.getId(),
                refreshToken,
                jwtUtil.getRefreshTokenExpire(),
                TimeUnit.SECONDS);

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtUtil.getAccessTokenExpire(),
                user.getId(),
                user.getUsername(),
                roles);
    }
}
