-- H2 兼容 Schema（开发环境，MySQL MODE）
-- 对应 V1 + V2 + V3 合并，去除 MySQL 特有语法

-- ===================== V1: 认证 / 用户 / 相册 =====================

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    email       VARCHAR(100) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    nickname    VARCHAR(50),
    avatar_url  VARCHAR(500),
    status      TINYINT NOT NULL DEFAULT 1,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_username UNIQUE (username),
    CONSTRAINT uk_email    UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS t_role (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(50) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS t_user_role (
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS t_album (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT NOT NULL,
    title                    VARCHAR(200) NOT NULL,
    description              TEXT,
    cover_url                VARCHAR(500),
    cover_media_id           BIGINT,
    cover_source_id          BIGINT,
    cover_source_type        VARCHAR(20),
    cover_source_name        VARCHAR(100),
    cover_external_media_key VARCHAR(255),
    cover_path               VARCHAR(500),
    cover_file_name          VARCHAR(255),
    cover_content_type       VARCHAR(100),
    cover_media_type         VARCHAR(20),
    bgm_url                  VARCHAR(500),
    bgm_media_id             BIGINT,
    bgm_source_id            BIGINT,
    bgm_source_type          VARCHAR(20),
    bgm_source_name          VARCHAR(100),
    bgm_external_media_key   VARCHAR(255),
    bgm_path                 VARCHAR(500),
    bgm_file_name            VARCHAR(255),
    bgm_content_type         VARCHAR(100),
    bgm_media_type           VARCHAR(20),
    bgm_volume               TINYINT DEFAULT 80,
    transition_style         VARCHAR(20) NOT NULL DEFAULT 'NONE',
    visibility               VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sort_order               INT DEFAULT 0,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  TINYINT NOT NULL DEFAULT 0
);

ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_media_id BIGINT;
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_source_id BIGINT;
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_source_type VARCHAR(20);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_source_name VARCHAR(100);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_external_media_key VARCHAR(255);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_path VARCHAR(500);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_file_name VARCHAR(255);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_content_type VARCHAR(100);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS cover_media_type VARCHAR(20);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_media_id BIGINT;
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_source_id BIGINT;
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_source_type VARCHAR(20);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_source_name VARCHAR(100);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_external_media_key VARCHAR(255);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_path VARCHAR(500);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_file_name VARCHAR(255);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_content_type VARCHAR(100);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS bgm_media_type VARCHAR(20);
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS transition_style VARCHAR(20) NOT NULL DEFAULT 'NONE';
UPDATE t_album SET transition_style = 'NONE' WHERE transition_style IS NULL OR transition_style = '';
ALTER TABLE t_album ADD COLUMN IF NOT EXISTS display_style VARCHAR(20) NOT NULL DEFAULT 'SINGLE';
UPDATE t_album SET display_style = 'SINGLE' WHERE display_style IS NULL OR display_style = '';

CREATE TABLE IF NOT EXISTS t_album_media (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    album_id           BIGINT NOT NULL,
    media_id           BIGINT,
    source_id          BIGINT,
    source_type        VARCHAR(20),
    source_name        VARCHAR(100),
    external_media_key VARCHAR(255),
    file_path          VARCHAR(500),
    file_name          VARCHAR(255),
    content_type       VARCHAR(100),
    media_type         VARCHAR(20),
    sort_order         INT DEFAULT 0,
    duration           INT,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_album_media UNIQUE (album_id, media_id)
);

ALTER TABLE t_album_media ALTER COLUMN media_id SET NULL;
ALTER TABLE t_album_media ADD COLUMN IF NOT EXISTS source_id BIGINT;
ALTER TABLE t_album_media ADD COLUMN IF NOT EXISTS source_type VARCHAR(20);
ALTER TABLE t_album_media ADD COLUMN IF NOT EXISTS source_name VARCHAR(100);
ALTER TABLE t_album_media ADD COLUMN IF NOT EXISTS external_media_key VARCHAR(255);
ALTER TABLE t_album_media ADD COLUMN IF NOT EXISTS file_path VARCHAR(500);
ALTER TABLE t_album_media ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE t_album_media ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);
ALTER TABLE t_album_media ADD COLUMN IF NOT EXISTS media_type VARCHAR(20);

CREATE TABLE IF NOT EXISTS t_album_bgm (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    album_id           BIGINT NOT NULL,
    media_id           BIGINT,
    source_id          BIGINT,
    source_type        VARCHAR(20),
    source_name        VARCHAR(100),
    external_media_key VARCHAR(255),
    file_path          VARCHAR(500),
    file_name          VARCHAR(255),
    content_type       VARCHAR(100),
    media_type         VARCHAR(20),
    sort_order         INT DEFAULT 0,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS source_id BIGINT;
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS source_type VARCHAR(20);
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS source_name VARCHAR(100);
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS external_media_key VARCHAR(255);
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS file_path VARCHAR(500);
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS media_type VARCHAR(20);
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS sort_order INT DEFAULT 0;
ALTER TABLE t_album_bgm ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uk_album_bgm_media ON t_album_bgm (album_id, media_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_album_bgm_external ON t_album_bgm (album_id, external_media_key);
CREATE INDEX IF NOT EXISTS idx_album_bgm_album_sort ON t_album_bgm (album_id, sort_order, id);

-- ===================== V2: 分发 / 审核 / 审计 =====================

CREATE TABLE IF NOT EXISTS t_distribution (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    album_id      BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    name          VARCHAR(100) NOT NULL,
    loop_play     TINYINT NOT NULL DEFAULT 1,
    shuffle       TINYINT NOT NULL DEFAULT 0,
    item_duration INT NOT NULL DEFAULT 10,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_distribution_device (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    distribution_id BIGINT NOT NULL,
    device_id       BIGINT,
    group_id        BIGINT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dist_device UNIQUE (distribution_id, device_id),
    CONSTRAINT uk_dist_group UNIQUE (distribution_id, group_id)
);

ALTER TABLE t_distribution_device ALTER COLUMN device_id BIGINT NULL;
ALTER TABLE t_distribution_device ADD COLUMN IF NOT EXISTS group_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_distribution_device_group_id ON t_distribution_device (group_id);

CREATE TABLE IF NOT EXISTS t_review_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    media_id      BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    reviewer_id   BIGINT,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(500),
    reviewed_at   DATETIME,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_review_setting (
    id                   BIGINT PRIMARY KEY,
    auto_approve_enabled TINYINT NOT NULL DEFAULT 0,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT,
    username      VARCHAR(50),
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id   VARCHAR(50),
    detail        TEXT,
    ip            VARCHAR(45),
    user_agent    VARCHAR(500),
    result        VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===================== V3: 媒体 / 设备 / 任务 =====================

CREATE TABLE IF NOT EXISTS t_media (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    media_type    VARCHAR(20) NOT NULL,
    file_size     BIGINT NOT NULL,
    source_type   VARCHAR(20) NOT NULL DEFAULT 'UPLOAD',
    source_id     BIGINT,
    source_name   VARCHAR(100) NOT NULL DEFAULT '上传',
    folder_path   VARCHAR(500) NOT NULL DEFAULT '/上传',
    origin_uri    VARCHAR(1000),
    ingest_mode   VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    bucket_name   VARCHAR(100) NOT NULL,
    object_key    VARCHAR(500) NOT NULL,
    thumbnail_key VARCHAR(500),
    duration_sec  INT,
    width         INT,
    height        INT,
    status        VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
    error_message VARCHAR(500),
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT NOT NULL DEFAULT 0
);

ALTER TABLE t_media ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'UPLOAD';
ALTER TABLE t_media ADD COLUMN IF NOT EXISTS source_id BIGINT;
ALTER TABLE t_media ADD COLUMN IF NOT EXISTS source_name VARCHAR(100) NOT NULL DEFAULT '上传';
ALTER TABLE t_media ADD COLUMN IF NOT EXISTS folder_path VARCHAR(500) NOT NULL DEFAULT '/上传';
ALTER TABLE t_media ADD COLUMN IF NOT EXISTS origin_uri VARCHAR(1000);
ALTER TABLE t_media ADD COLUMN IF NOT EXISTS ingest_mode VARCHAR(20) NOT NULL DEFAULT 'UPLOADED';

UPDATE t_media SET source_type = 'UPLOAD' WHERE source_type IS NULL OR source_type = '';
UPDATE t_media SET source_name = '上传' WHERE source_name IS NULL OR source_name = '';
UPDATE t_media SET folder_path = '/上传' WHERE folder_path IS NULL OR folder_path = '';
UPDATE t_media SET ingest_mode = 'UPLOADED' WHERE ingest_mode IS NULL OR ingest_mode = '';
UPDATE t_media SET origin_uri = object_key WHERE (origin_uri IS NULL OR origin_uri = '') AND object_key IS NOT NULL AND object_key <> '';

CREATE INDEX IF NOT EXISTS idx_media_source_type ON t_media (source_type);
CREATE INDEX IF NOT EXISTS idx_media_source_id ON t_media (source_id);
CREATE INDEX IF NOT EXISTS idx_media_folder_path ON t_media (folder_path);

CREATE TABLE IF NOT EXISTS t_media_source (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    source_type           VARCHAR(20) NOT NULL DEFAULT 'SMB',
    config_json           CLOB,
    credential_ciphertext CLOB,
    bound_path            VARCHAR(500),
    bound_path_name       VARCHAR(255),
    host                  VARCHAR(255) NOT NULL,
    port                  INT NOT NULL DEFAULT 445,
    share_name            VARCHAR(255) NOT NULL,
    root_path             VARCHAR(500) NOT NULL DEFAULT '/',
    username              VARCHAR(255) NOT NULL,
    password_ciphertext   VARCHAR(1000),
    enabled               TINYINT NOT NULL DEFAULT 1,
    last_scan_at          DATETIME,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               TINYINT NOT NULL DEFAULT 0
);

ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS user_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'SMB';
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS config_json CLOB;
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS credential_ciphertext CLOB;
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS bound_path VARCHAR(500);
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS bound_path_name VARCHAR(255);
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS host VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS port INT NOT NULL DEFAULT 445;
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS share_name VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS root_path VARCHAR(500) NOT NULL DEFAULT '/';
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS username VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS password_ciphertext VARCHAR(1000);
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS enabled TINYINT NOT NULL DEFAULT 1;
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS last_scan_at DATETIME;
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE t_media_source ADD COLUMN IF NOT EXISTS deleted TINYINT NOT NULL DEFAULT 0;

UPDATE t_media_source SET source_type = 'SMB' WHERE source_type IS NULL OR source_type = '';
UPDATE t_media_source SET port = 445 WHERE port IS NULL OR port <= 0;
UPDATE t_media_source SET root_path = '/' WHERE root_path IS NULL OR root_path = '';
UPDATE t_media_source SET enabled = 1 WHERE enabled IS NULL;
UPDATE t_media_source SET bound_path = root_path WHERE bound_path IS NULL OR bound_path = '';
UPDATE t_media_source SET bound_path_name = name WHERE bound_path_name IS NULL OR bound_path_name = '';

CREATE INDEX IF NOT EXISTS idx_media_source_user_id ON t_media_source (user_id);
CREATE INDEX IF NOT EXISTS idx_media_source_type_deleted ON t_media_source (source_type, deleted);

CREATE TABLE IF NOT EXISTS t_upload_session (
    upload_id      VARCHAR(64) PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    file_size      BIGINT NOT NULL,
    total_parts    INT NOT NULL,
    uploaded_parts INT NOT NULL DEFAULT 0,
    object_key     VARCHAR(500) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'INIT',
    expires_at     DATETIME,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_upload_part (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id       VARCHAR(64) NOT NULL,
    part_number     INT NOT NULL,
    etag            VARCHAR(128),
    part_size       BIGINT,
    part_object_key VARCHAR(500) NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_upload_part UNIQUE (upload_id, part_number)
);

CREATE TABLE IF NOT EXISTS t_media_process_task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    media_id      BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    task_type     VARCHAR(30) NOT NULL DEFAULT 'MEDIA_PROCESS',
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count   INT NOT NULL DEFAULT 0,
    max_retry     INT NOT NULL DEFAULT 3,
    next_run_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at    DATETIME,
    finished_at   DATETIME,
    error_message VARCHAR(500),
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_device (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT,
    device_uid        VARCHAR(128) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    type              VARCHAR(50) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    last_heartbeat_at DATETIME,
    bound_at          DATETIME,
    unbound_at        DATETIME,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_device_uid_deleted UNIQUE (device_uid, deleted)
);

ALTER TABLE t_device ALTER COLUMN user_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS t_device_group (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_device_group_rel (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id   BIGINT NOT NULL,
    device_id  BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_device UNIQUE (group_id, device_id)
);

-- ===================== 初始化数据 =====================

MERGE INTO t_role (code, name) KEY (code)
    VALUES ('ROLE_USER', '普通用户'), ('ROLE_ADMIN', '管理员');
