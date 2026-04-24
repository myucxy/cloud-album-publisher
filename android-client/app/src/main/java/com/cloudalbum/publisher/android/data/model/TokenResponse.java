package com.cloudalbum.publisher.android.data.model;

import java.util.List;

public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private long accessTokenExpire;
    private long userId;
    private String username;
    private List<String> roles;

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getAccessTokenExpire() {
        return accessTokenExpire;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getRoles() {
        return roles;
    }
}
