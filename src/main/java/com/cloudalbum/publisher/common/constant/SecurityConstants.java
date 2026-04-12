package com.cloudalbum.publisher.common.constant;

public final class SecurityConstants {

    private SecurityConstants() {}

    /** Authorization 请求头名称 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer Token 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Access Token 存储的 claim：用户ID */
    public static final String CLAIM_USER_ID = "uid";

    /** Access Token 存储的 claim：用户名 */
    public static final String CLAIM_USERNAME = "username";

    /** Access Token 存储的 claim：角色列表 */
    public static final String CLAIM_ROLES = "roles";

    /** 设备 Token 存储的 claim：设备ID */
    public static final String CLAIM_DEVICE_ID = "deviceId";

    /** 设备 Token 存储的 claim：设备唯一ID */
    public static final String CLAIM_DEVICE_UID = "deviceUid";

    /** Token 类型 claim */
    public static final String CLAIM_TOKEN_TYPE = "type";

    /** Access Token 类型标识 */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** Refresh Token 类型标识 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** 设备 Access Token 类型标识 */
    public static final String TOKEN_TYPE_DEVICE_ACCESS = "device_access";

    /** 设备类型请求头 */
    public static final String HEADER_DEVICE_TYPE = "X-Device-Type";

    /** 设备唯一ID请求头 */
    public static final String HEADER_DEVICE_ID = "X-Device-ID";
}
