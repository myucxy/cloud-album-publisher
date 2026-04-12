-- V2: 分发、审核、审计模块（C负责）
-- 依赖：t_album（V1），t_device（B负责，使用 BIGINT UNSIGNED 关联）

-- -------------------------------------------------------
-- 1. 分发规则表
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_distribution (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '分发规则ID',
    album_id         BIGINT UNSIGNED NOT NULL COMMENT '相册ID（关联t_album）',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '创建者用户ID',
    name             VARCHAR(100) NOT NULL COMMENT '规则名称',
    loop_play        TINYINT NOT NULL DEFAULT 1 COMMENT '是否循环播放：1是 0否',
    shuffle          TINYINT NOT NULL DEFAULT 0 COMMENT '是否随机播放：1是 0否',
    item_duration    INT NOT NULL DEFAULT 10 COMMENT '每张展示时长（秒）',
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ACTIVE/DISABLED',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_album_id (album_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容分发规则表';

-- -------------------------------------------------------
-- 2. 分发规则-设备关联表（一条规则可关联多个设备）
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_distribution_device (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    distribution_id BIGINT UNSIGNED NOT NULL COMMENT '分发规则ID',
    device_id       BIGINT UNSIGNED NULL COMMENT '设备ID（关联B负责的t_device表）',
    group_id        BIGINT UNSIGNED NULL COMMENT '设备组ID（关联B负责的t_device_group表）',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    UNIQUE KEY uk_dist_device (distribution_id, device_id),
    UNIQUE KEY uk_dist_group (distribution_id, group_id),
    INDEX idx_distribution_id (distribution_id),
    INDEX idx_device_id (device_id),
    INDEX idx_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分发规则-设备/设备组关联表';

-- -------------------------------------------------------
-- 3. 审核记录表
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_review_record (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '审核记录ID',
    media_id     BIGINT UNSIGNED NOT NULL COMMENT '媒体ID（关联B负责的t_media表）',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '上传者用户ID',
    reviewer_id  BIGINT UNSIGNED COMMENT '审核人用户ID（NULL=待审核）',
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
    reject_reason VARCHAR(500) COMMENT '驳回原因（status=REJECTED时填写）',
    reviewed_at  DATETIME COMMENT '审核时间',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_media_id (media_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_reviewer_id (reviewer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容审核记录表';

-- -------------------------------------------------------
-- 4. 操作审计日志表（不做逻辑删除，只归档）
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_audit_log (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id      BIGINT UNSIGNED COMMENT '操作用户ID（NULL=匿名）',
    username     VARCHAR(50) COMMENT '操作用户名（快照，防止用户被删后查不到）',
    action       VARCHAR(100) NOT NULL COMMENT '操作类型，如：USER_LOGIN / ALBUM_DELETE',
    resource_type VARCHAR(50) COMMENT '资源类型，如：album / media / distribution',
    resource_id  VARCHAR(50) COMMENT '资源ID（字符串兼容各类ID）',
    detail       TEXT COMMENT '操作详情（JSON格式）',
    ip           VARCHAR(45) COMMENT '客户端IP（支持IPv6）',
    user_agent   VARCHAR(500) COMMENT '客户端User-Agent',
    result       VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果：SUCCESS/FAIL',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志表';
