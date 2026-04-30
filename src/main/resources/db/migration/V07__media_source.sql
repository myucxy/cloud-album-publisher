USE cloud_album;

CREATE TABLE IF NOT EXISTS t_media_source (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '媒体源ID',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    name                VARCHAR(100) NOT NULL COMMENT '媒体源名称',
    source_type         VARCHAR(20) NOT NULL DEFAULT 'SMB' COMMENT '来源类型',
    host                VARCHAR(255) NOT NULL COMMENT '主机地址',
    port                INT NOT NULL DEFAULT 445 COMMENT '端口',
    share_name          VARCHAR(255) NOT NULL COMMENT '共享名称',
    root_path           VARCHAR(500) NOT NULL DEFAULT '/' COMMENT '根目录',
    username            VARCHAR(255) NOT NULL COMMENT '用户名',
    password_ciphertext VARCHAR(1000) NULL COMMENT '加密后的密码',
    enabled             TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_scan_at        DATETIME NULL COMMENT '最近扫描时间',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_media_source_user_id (user_id),
    INDEX idx_media_source_type_deleted (source_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体源表';
