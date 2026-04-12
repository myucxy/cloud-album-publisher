package com.cloudalbum.publisher.common.constant;

public final class CacheConstants {

    private CacheConstants() {}

    /** Refresh Token：refresh_token:{userId} */
    public static final String REFRESH_TOKEN_KEY = "refresh_token:";

    /** Access Token 黑名单：token_blacklist:{jti} */
    public static final String TOKEN_BLACKLIST_KEY = "token_blacklist:";

    /** 用户信息缓存：user_info:{userId} */
    public static final String USER_INFO_KEY = "user_info:";

    /** 相册缓存：album:{albumId} */
    public static final String ALBUM_KEY = "album:";

    /** 上传进度缓存：upload:progress:{uploadId} */
    public static final String UPLOAD_PROGRESS_KEY = "upload:progress:";

    /** 媒体任务分布式锁：task:media:lock:{taskId} */
    public static final String MEDIA_TASK_LOCK_KEY = "task:media:lock:";
}
