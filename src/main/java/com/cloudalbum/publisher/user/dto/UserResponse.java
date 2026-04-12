package com.cloudalbum.publisher.user.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String nickname;
    private String avatarUrl;
    private Integer status;
    private List<String> roles;
    private LocalDateTime createdAt;
}
