package com.cloudalbum.publisher.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private long accessTokenExpire;
    private long userId;
    private String username;
    private List<String> roles;
}
