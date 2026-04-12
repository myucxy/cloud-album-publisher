package com.cloudalbum.publisher.auth.service;

import com.cloudalbum.publisher.auth.dto.ChangePasswordRequest;
import com.cloudalbum.publisher.auth.dto.LoginRequest;
import com.cloudalbum.publisher.auth.dto.RefreshRequest;
import com.cloudalbum.publisher.auth.dto.RegisterRequest;
import com.cloudalbum.publisher.auth.dto.TokenResponse;

public interface AuthService {

    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshRequest request);

    void logout(String accessToken);

    void changePassword(Long userId, ChangePasswordRequest request);
}
