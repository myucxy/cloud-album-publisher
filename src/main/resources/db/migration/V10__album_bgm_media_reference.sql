ALTER TABLE t_album
    ADD COLUMN bgm_media_id BIGINT NULL COMMENT '内部BGM媒体ID',
    ADD COLUMN bgm_source_id BIGINT NULL COMMENT '外部BGM媒体源ID',
    ADD COLUMN bgm_source_type VARCHAR(20) NULL COMMENT '外部BGM媒体源类型',
    ADD COLUMN bgm_source_name VARCHAR(100) NULL COMMENT '外部BGM媒体源名称',
    ADD COLUMN bgm_external_media_key VARCHAR(255) NULL COMMENT '外部BGM媒体引用Key',
    ADD COLUMN bgm_path VARCHAR(500) NULL COMMENT '外部BGM文件路径',
    ADD COLUMN bgm_file_name VARCHAR(255) NULL COMMENT 'BGM文件名',
    ADD COLUMN bgm_content_type VARCHAR(100) NULL COMMENT 'BGM内容类型',
    ADD COLUMN bgm_media_type VARCHAR(20) NULL COMMENT 'BGM媒体类型';
