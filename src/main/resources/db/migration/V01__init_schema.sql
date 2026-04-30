-- V1: 初始化数据库表结构
-- 用户A负责：认证、用户、相册模块

CREATE DATABASE IF NOT EXISTS cloud_album DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cloud_album;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    email       VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    password    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt）',
    nickname    VARCHAR(50)  COMMENT '昵称',
    avatar_url  VARCHAR(500) COMMENT '头像URL',
    status      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：1已删除',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS t_role (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    code       VARCHAR(50) NOT NULL UNIQUE COMMENT '角色代码：ROLE_USER/ROLE_ADMIN',
    name       VARCHAR(50) NOT NULL COMMENT '角色名称',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS t_user_role (
    user_id  BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    role_id  BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 相册表
CREATE TABLE IF NOT EXISTS t_album (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '相册ID',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    title        VARCHAR(200) NOT NULL COMMENT '相册名称',
    description  TEXT COMMENT '相册描述',
    cover_url    VARCHAR(500) COMMENT '封面图URL',
    bgm_url      VARCHAR(500) COMMENT 'BGM音频URL',
    bgm_volume   TINYINT DEFAULT 80 COMMENT 'BGM音量 0-100',
    visibility   VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' COMMENT '可见性：PUBLIC/PRIVATE/DEVICE_ONLY',
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED',
    sort_order   INT DEFAULT 0 COMMENT '排序权重',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_user_id (user_id),
    INDEX idx_visibility (visibility),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相册表';

-- 相册内容关联表（A定义结构，B填充媒体数据）
CREATE TABLE IF NOT EXISTS t_album_media (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    album_id   BIGINT UNSIGNED NOT NULL COMMENT '相册ID',
    media_id   BIGINT UNSIGNED NOT NULL COMMENT '媒体ID（关联B负责的t_media表）',
    sort_order INT DEFAULT 0 COMMENT '展示排序',
    duration   INT COMMENT '展示时长（秒）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    UNIQUE KEY uk_album_media (album_id, media_id),
    INDEX idx_album_id (album_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相册内容关联表';

-- 初始化角色数据
INSERT IGNORE INTO t_role (code, name) VALUES
    ('ROLE_USER', '普通用户'),
    ('ROLE_ADMIN', '管理员');
