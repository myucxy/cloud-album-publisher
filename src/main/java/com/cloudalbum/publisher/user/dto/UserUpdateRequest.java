package com.cloudalbum.publisher.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {

    @Size(max = 50, message = "昵称最长50位")
    private String nickname;

    @Size(max = 500, message = "头像URL过长")
    private String avatarUrl;
}
