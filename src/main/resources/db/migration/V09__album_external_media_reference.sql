USE cloud_album;

ALTER TABLE t_album
    ADD COLUMN cover_source_id BIGINT NULL COMMENT '外部封面媒体源ID',
    ADD COLUMN cover_source_type VARCHAR(20) NULL COMMENT '外部封面媒体源类型',
    ADD COLUMN cover_source_name VARCHAR(100) NULL COMMENT '外部封面媒体源名称',
    ADD COLUMN cover_external_media_key VARCHAR(255) NULL COMMENT '外部封面媒体引用Key',
    ADD COLUMN cover_path VARCHAR(500) NULL COMMENT '外部封面文件路径',
    ADD COLUMN cover_file_name VARCHAR(255) NULL COMMENT '外部封面文件名',
    ADD COLUMN cover_content_type VARCHAR(100) NULL COMMENT '外部封面内容类型',
    ADD COLUMN cover_media_type VARCHAR(20) NULL COMMENT '外部封面媒体类型';

ALTER TABLE t_album_media
    MODIFY COLUMN media_id BIGINT NULL COMMENT '内部媒体ID',
    ADD COLUMN source_id BIGINT NULL COMMENT '外部媒体源ID',
    ADD COLUMN source_type VARCHAR(20) NULL COMMENT '外部媒体源类型',
    ADD COLUMN source_name VARCHAR(100) NULL COMMENT '外部媒体源名称',
    ADD COLUMN external_media_key VARCHAR(255) NULL COMMENT '外部媒体引用Key',
    ADD COLUMN file_path VARCHAR(500) NULL COMMENT '外部媒体文件路径',
    ADD COLUMN file_name VARCHAR(255) NULL COMMENT '媒体文件名',
    ADD COLUMN content_type VARCHAR(100) NULL COMMENT '内容类型',
    ADD COLUMN media_type VARCHAR(20) NULL COMMENT '媒体类型';

ALTER TABLE t_album_media
    ADD UNIQUE KEY uk_album_external_media (album_id, external_media_key);
