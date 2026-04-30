USE cloud_album;

-- V2: 模块B（媒体、设备、任务）表结构

-- 媒体表
CREATE TABLE IF NOT EXISTS t_media (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '媒体ID',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    file_name     VARCHAR(255) NOT NULL COMMENT '文件名',
    content_type  VARCHAR(100) NOT NULL COMMENT 'MIME类型',
    media_type    VARCHAR(20) NOT NULL COMMENT 'IMAGE/VIDEO/AUDIO/OTHER',
    file_size     BIGINT UNSIGNED NOT NULL COMMENT '文件大小（字节）',
    bucket_name   VARCHAR(100) NOT NULL COMMENT '存储桶',
    object_key    VARCHAR(500) NOT NULL COMMENT '对象存储Key',
    thumbnail_key VARCHAR(500) COMMENT '缩略图对象Key',
    duration_sec  INT COMMENT '时长（秒）',
    width         INT COMMENT '宽度',
    height        INT COMMENT '高度',
    status        VARCHAR(20) NOT NULL DEFAULT 'UPLOADING' COMMENT 'UPLOADING/UPLOADED/PROCESSING/READY/FAILED',
    error_message VARCHAR(500) COMMENT '处理失败原因',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_media_user_id (user_id),
    INDEX idx_media_status (status),
    INDEX idx_media_type (media_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体表';

-- 上传会话表
CREATE TABLE IF NOT EXISTS t_upload_session (
    upload_id      VARCHAR(64) PRIMARY KEY COMMENT '上传会话ID',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    file_name      VARCHAR(255) NOT NULL COMMENT '文件名',
    content_type   VARCHAR(100) NOT NULL COMMENT 'MIME类型',
    file_size      BIGINT UNSIGNED NOT NULL COMMENT '文件总大小（字节）',
    total_parts    INT NOT NULL COMMENT '总分片数',
    uploaded_parts INT NOT NULL DEFAULT 0 COMMENT '已上传分片数',
    object_key     VARCHAR(500) NOT NULL COMMENT '最终对象Key',
    status         VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT 'INIT/UPLOADING/COMPLETED/FAILED',
    expires_at     DATETIME COMMENT '过期时间',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_upload_user_id (user_id),
    INDEX idx_upload_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传会话表';

-- 上传分片表
CREATE TABLE IF NOT EXISTS t_upload_part (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '分片记录ID',
    upload_id       VARCHAR(64) NOT NULL COMMENT '上传会话ID',
    part_number     INT NOT NULL COMMENT '分片序号，从1开始',
    etag            VARCHAR(128) COMMENT '分片ETag',
    part_size       BIGINT UNSIGNED COMMENT '分片大小（字节）',
    part_object_key VARCHAR(500) NOT NULL COMMENT '分片对象Key',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_upload_part (upload_id, part_number),
    INDEX idx_upload_part_upload_id (upload_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传记录表';

-- 媒体处理任务表
CREATE TABLE IF NOT EXISTS t_media_process_task (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    media_id      BIGINT UNSIGNED NOT NULL COMMENT '媒体ID',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    task_type     VARCHAR(30) NOT NULL DEFAULT 'MEDIA_PROCESS' COMMENT '任务类型',
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/RETRY_WAIT',
    retry_count   INT NOT NULL DEFAULT 0 COMMENT '当前重试次数',
    max_retry     INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_run_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次执行时间',
    started_at    DATETIME COMMENT '开始执行时间',
    finished_at   DATETIME COMMENT '完成时间',
    error_message VARCHAR(500) COMMENT '错误信息',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_task_media_id (media_id),
    INDEX idx_task_status_next_run (status, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体处理任务表';

-- 设备表
CREATE TABLE IF NOT EXISTS t_device (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '设备ID',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    device_uid        VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
    name              VARCHAR(100) NOT NULL COMMENT '设备名称',
    type              VARCHAR(50) NOT NULL COMMENT '设备类型',
    status            VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT 'UNBOUND/OFFLINE/ONLINE',
    last_heartbeat_at DATETIME COMMENT '最后心跳时间',
    bound_at          DATETIME COMMENT '绑定时间',
    unbound_at        DATETIME COMMENT '解绑时间',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_device_uid_deleted (device_uid, deleted),
    INDEX idx_device_user_id (user_id),
    INDEX idx_device_status (status),
    INDEX idx_device_heartbeat (last_heartbeat_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- 设备组表
CREATE TABLE IF NOT EXISTS t_device_group (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '分组ID',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    name        VARCHAR(100) NOT NULL COMMENT '分组名称',
    description VARCHAR(500) COMMENT '分组描述',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_device_group_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分组表';

-- 设备组成员关系表
CREATE TABLE IF NOT EXISTS t_device_group_rel (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '关系ID',
    group_id   BIGINT UNSIGNED NOT NULL COMMENT '设备组ID',
    device_id  BIGINT UNSIGNED NOT NULL COMMENT '设备ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_group_device (group_id, device_id),
    INDEX idx_group_id (group_id),
    INDEX idx_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备组与设备关系表';
