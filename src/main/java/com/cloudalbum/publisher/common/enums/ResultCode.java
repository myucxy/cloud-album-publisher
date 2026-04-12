package com.cloudalbum.publisher.common.enums;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证，请先登录"),
    FORBIDDEN(403, "无权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 认证相关
    TOKEN_EXPIRED(401, "Token已过期"),
    TOKEN_INVALID(401, "Token无效"),
    USERNAME_OR_PASSWORD_ERROR(401, "用户名或密码错误"),
    OLD_PASSWORD_INCORRECT(400, "旧密码错误"),
    ACCOUNT_DISABLED(403, "账号已被禁用"),

    // 用户相关
    USER_NOT_FOUND(404, "用户不存在"),
    USERNAME_ALREADY_EXISTS(409, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(409, "邮箱已被注册"),

    // 相册相关
    ALBUM_NOT_FOUND(404, "相册不存在"),
    ALBUM_ACCESS_DENIED(403, "无权访问该相册"),

    // 媒体相关
    MEDIA_NOT_FOUND(404, "媒体不存在"),
    MEDIA_ACCESS_DENIED(403, "无权访问该媒体"),
    MEDIA_NOT_READY(409, "媒体尚未就绪"),
    MEDIA_UPLOAD_INIT_FAILED(500, "初始化上传失败"),
    UPLOAD_SESSION_NOT_FOUND(404, "上传会话不存在"),
    UPLOAD_PART_MISSING(400, "分片上传不完整"),
    UPLOAD_ALREADY_COMPLETED(409, "上传会话已完成"),

    // 设备相关
    DEVICE_NOT_FOUND(404, "设备不存在"),
    DEVICE_ACCESS_DENIED(403, "无权访问该设备"),
    DEVICE_ALREADY_BOUND(409, "设备已被绑定"),
    DEVICE_GROUP_NOT_FOUND(404, "设备组不存在"),
    DEVICE_GROUP_MEMBER_EXISTS(409, "设备已在该分组中"),
    DEVICE_GROUP_MEMBER_NOT_FOUND(404, "设备不在该分组中"),

    // 任务相关
    TASK_NOT_FOUND(404, "任务不存在"),
    TASK_EXECUTION_FAILED(500, "任务执行失败"),

    // 分发相关
    DISTRIBUTION_NOT_FOUND(404, "分发规则不存在"),
    DISTRIBUTION_ACCESS_DENIED(403, "无权操作该分发规则"),
    DISTRIBUTION_ALREADY_ACTIVE(409, "分发规则已处于生效状态"),
    DISTRIBUTION_ALREADY_DISABLED(409, "分发规则已处于停用状态"),

    // 审核相关
    REVIEW_NOT_FOUND(404, "审核记录不存在"),
    REVIEW_ALREADY_PROCESSED(409, "该内容已完成审核，不可重复操作"),
    MEDIA_REVIEW_PENDING(403, "媒体内容尚未通过审核");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
