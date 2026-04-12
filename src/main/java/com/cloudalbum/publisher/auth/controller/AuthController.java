package com.cloudalbum.publisher.auth.controller;

import com.cloudalbum.publisher.auth.dto.ChangePasswordRequest;
import com.cloudalbum.publisher.auth.dto.LoginRequest;
import com.cloudalbum.publisher.auth.dto.RefreshRequest;
import com.cloudalbum.publisher.auth.dto.RegisterRequest;
import com.cloudalbum.publisher.auth.dto.TokenResponse;
import com.cloudalbum.publisher.auth.service.AuthService;
import com.cloudalbum.publisher.common.constant.SecurityConstants;
import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "注册/登录/Token刷新/登出")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        authService.logout(header);
        return Result.success();
    }

    @Operation(summary = "修改当前用户密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtil.getCurrentUserId(), request);
        return Result.success();
    }
}
