USE cloud_album;

ALTER TABLE t_device MODIFY COLUMN user_id BIGINT UNSIGNED NULL COMMENT '绑定用户ID，未绑定时为空';
