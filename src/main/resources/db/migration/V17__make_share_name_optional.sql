-- 将 share_name 列改为可选，因为 SMB shareName 现在合并到 rootPath 中
ALTER TABLE t_media_source MODIFY COLUMN share_name VARCHAR(255) DEFAULT '';
