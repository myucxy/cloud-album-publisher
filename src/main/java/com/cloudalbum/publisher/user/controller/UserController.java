package com.cloudalbum.publisher.user.controller;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import com.cloudalbum.publisher.common.model.PageRequest;
import com.cloudalbum.publisher.common.model.PageResult;
import com.cloudalbum.publisher.common.model.Result;
import com.cloudalbum.publisher.common.util.SecurityUtil;
import com.cloudalbum.publisher.user.dto.UserResponse;
import com.cloudalbum.publisher.user.dto.UserUpdateRequest;
import com.cloudalbum.publisher.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取用户列表（管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<UserResponse>> listUsers(@Valid PageRequest pageRequest) {
        return Result.success(userService.listUsers(pageRequest));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<UserResponse> getUser(@PathVariable Long id) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!currentUserId.equals(id) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.success(userService.getUserById(id));
    }

    @Operation(summary = "全量更新用户信息")
    @PutMapping("/{id}")
    public Result<UserResponse> updateUser(@PathVariable Long id,
                                           @Valid @RequestBody UserUpdateRequest request) {
        checkOwnerOrAdmin(id);
        return Result.success(userService.updateUser(id, request));
    }

    @Operation(summary = "部分更新用户信息")
    @PatchMapping("/{id}")
    public Result<UserResponse> patchUser(@PathVariable Long id,
                                          @RequestBody UserUpdateRequest request) {
        checkOwnerOrAdmin(id);
        return Result.success(userService.updateUser(id, request));
    }

    @Operation(summary = "删除用户（管理员）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    private void checkOwnerOrAdmin(Long id) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!currentUserId.equals(id) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
