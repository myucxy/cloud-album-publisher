USE cloud_album;

ALTER TABLE t_album
    ADD COLUMN cover_media_id BIGINT NULL COMMENT '封面媒体ID';
