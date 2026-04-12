package com.cloudalbum.publisher.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.user.dto.UserResponse;
import com.cloudalbum.publisher.user.dto.UserUpdateRequest;
import com.cloudalbum.publisher.user.entity.User;
import com.cloudalbum.publisher.user.mapper.UserMapper;
import com.cloudalbum.publisher.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public PageResult<UserResponse> listUsers(PageRequest pageRequest) {
        IPage<User> page = userMapper.selectPage(
                new Page<>(pageRequest.getPage(), pageRequest.getSize()), null);
        List<UserResponse> list = page.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), pageRequest.getPage(), pageRequest.getSize(), list);
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toResponse(user);
    }

    @Override
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        userMapper.updateById(user);
        return toResponse(user);
    }

    @Override
    public void deleteUser(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        userMapper.deleteById(id);
    }

    private UserResponse toResponse(User user) {
        UserResponse resp = new UserResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setEmail(user.getEmail());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setStatus(user.getStatus());
        resp.setCreatedAt(user.getCreatedAt());
        resp.setRoles(userMapper.selectRoleCodesByUserId(user.getId()));
        return resp;
    }
}
