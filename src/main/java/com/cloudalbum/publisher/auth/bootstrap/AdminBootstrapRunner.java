package com.cloudalbum.publisher.auth.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.user.entity.User;
import com.cloudalbum.publisher.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
    private static final int PASSWORD_LENGTH = 16;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${bootstrap.admin.enabled:true}")
    private boolean enabled;

    @Value("${bootstrap.admin.username:admin}")
    private String username;

    @Value("${bootstrap.admin.email:admin@cloud-album.local}")
    private String email;

    @Value("${bootstrap.admin.nickname:System Admin}")
    private String nickname;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (userMapper.countAdminUsers() > 0) {
            return;
        }
        bootstrapAdmin();
    }

    private void bootstrapAdmin() {
        User existingByUsername = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        User existingByEmail = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .last("LIMIT 1"));

        if (existingByUsername != null || existingByEmail != null) {
            User existing = resolveExistingUser(existingByUsername, existingByEmail);
            userMapper.insertUserRole(existing.getId(), ADMIN_ROLE);
            log.warn("Granted {} to existing user username={}, email={}. No password was changed.",
                    ADMIN_ROLE, existing.getUsername(), existing.getEmail());
            return;
        }

        String rawPassword = generatePassword();
        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setNickname(StringUtils.hasText(nickname) ? nickname : username);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setStatus(1);
        userMapper.insert(admin);
        userMapper.insertUserRole(admin.getId(), ADMIN_ROLE);

        log.warn("Default admin account created. username={}, email={}, initialPassword={}. This password is shown only once, please change it immediately.",
                admin.getUsername(), admin.getEmail(), rawPassword);
    }

    private User resolveExistingUser(User existingByUsername, User existingByEmail) {
        if (existingByUsername != null && existingByEmail != null && !existingByUsername.getId().equals(existingByEmail.getId())) {
            throw new IllegalStateException("Cannot bootstrap admin because username and email belong to different users");
        }
        if (existingByUsername != null) {
            return existingByUsername;
        }
        return existingByEmail;
    }

    private String generatePassword() {
        StringBuilder builder = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = secureRandom.nextInt(PASSWORD_CHARS.length());
            builder.append(PASSWORD_CHARS.charAt(index));
        }
        return builder.toString();
    }
}
