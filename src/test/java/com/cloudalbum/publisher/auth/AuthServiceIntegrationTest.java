package com.cloudalbum.publisher.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.CloudAlbumPublisherApplication;
import com.cloudalbum.publisher.auth.dto.ChangePasswordRequest;
import com.cloudalbum.publisher.auth.dto.LoginRequest;
import com.cloudalbum.publisher.auth.dto.RefreshRequest;
import com.cloudalbum.publisher.auth.dto.RegisterRequest;
import com.cloudalbum.publisher.auth.dto.TokenResponse;
import com.cloudalbum.publisher.auth.service.AuthService;
import com.cloudalbum.publisher.common.constant.CacheConstants;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.user.entity.User;
import com.cloudalbum.publisher.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = CloudAlbumPublisherApplication.class)
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void changesPasswordAndInvalidatesRefreshToken() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("change-password-user");
        registerRequest.setEmail("change-password-user@cloud-album.local");
        registerRequest.setPassword("oldPass123");
        registerRequest.setNickname("Change Password User");
        TokenResponse registerResponse = authService.register(registerRequest);

        String refreshTokenKey = CacheConstants.REFRESH_TOKEN_KEY + registerResponse.getUserId();
        String oldRefreshToken = registerResponse.getRefreshToken();
        User beforeUpdate = userMapper.selectById(registerResponse.getUserId());

        ChangePasswordRequest changePasswordRequest = new ChangePasswordRequest();
        changePasswordRequest.setOldPassword("oldPass123");
        changePasswordRequest.setNewPassword("newPass456");
        authService.changePassword(registerResponse.getUserId(), changePasswordRequest);

        User afterUpdate = userMapper.selectById(registerResponse.getUserId());
        assertNotEquals(beforeUpdate.getPassword(), afterUpdate.getPassword());
        assertFalse(passwordEncoder.matches("oldPass123", afterUpdate.getPassword()));
        assertTrue(passwordEncoder.matches("newPass456", afterUpdate.getPassword()));
        assertEquals(null, redisTemplate.opsForValue().get(refreshTokenKey));

        LoginRequest oldLoginRequest = new LoginRequest();
        oldLoginRequest.setUsername("change-password-user");
        oldLoginRequest.setPassword("oldPass123");
        assertThrows(Exception.class, () -> authService.login(oldLoginRequest));

        LoginRequest newLoginRequest = new LoginRequest();
        newLoginRequest.setUsername("change-password-user");
        newLoginRequest.setPassword("newPass456");
        TokenResponse newLoginResponse = authService.login(newLoginRequest);
        assertEquals(registerResponse.getUserId(), newLoginResponse.getUserId());

        RefreshRequest refreshRequest = new RefreshRequest();
        refreshRequest.setRefreshToken(oldRefreshToken);
        assertThrows(Exception.class, () -> authService.refresh(refreshRequest));
    }

    @Test
    void rejectsChangeWhenOldPasswordIsIncorrect() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("wrong-old-password-user");
        registerRequest.setEmail("wrong-old-password-user@cloud-album.local");
        registerRequest.setPassword("oldPass123");
        registerRequest.setNickname("Wrong Old Password User");
        TokenResponse registerResponse = authService.register(registerRequest);

        User beforeUpdate = userMapper.selectById(registerResponse.getUserId());

        ChangePasswordRequest changePasswordRequest = new ChangePasswordRequest();
        changePasswordRequest.setOldPassword("incorrectOldPass");
        changePasswordRequest.setNewPassword("newPass456");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.changePassword(registerResponse.getUserId(), changePasswordRequest));
        assertEquals(ResultCode.OLD_PASSWORD_INCORRECT.getCode(), exception.getCode());

        User afterUpdate = userMapper.selectById(registerResponse.getUserId());
        assertEquals(beforeUpdate.getPassword(), afterUpdate.getPassword());
        assertTrue(passwordEncoder.matches("oldPass123", afterUpdate.getPassword()));
    }

    @Test
    void canLoginWithChangedPasswordAfterManualSetup() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "manual-change-password-user")
                .last("LIMIT 1"));
        if (user == null) {
            user = new User();
            user.setUsername("manual-change-password-user");
            user.setEmail("manual-change-password-user@cloud-album.local");
            user.setPassword(passwordEncoder.encode("startPass123"));
            user.setNickname("Manual User");
            user.setStatus(1);
            userMapper.insert(user);
            userMapper.insertUserRole(user.getId(), "ROLE_USER");
        }

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("startPass123");
        request.setNewPassword("finishPass456");
        authService.changePassword(user.getId(), request);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("manual-change-password-user");
        loginRequest.setPassword("finishPass456");
        TokenResponse response = authService.login(loginRequest);
        assertEquals(user.getId(), response.getUserId());
    }
}
