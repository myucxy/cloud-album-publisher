package com.cloudalbum.publisher.user.service;

import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.user.dto.UserResponse;
import com.cloudalbum.publisher.user.dto.UserUpdateRequest;

public interface UserService {

    PageResult<UserResponse> listUsers(PageRequest pageRequest);

    UserResponse getUserById(Long id);

    UserResponse updateUser(Long id, UserUpdateRequest request);

    void deleteUser(Long id);
}
