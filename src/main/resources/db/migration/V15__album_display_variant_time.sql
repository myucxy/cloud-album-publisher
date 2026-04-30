USE cloud_album;

ALTER TABLE t_album
    ADD COLUMN display_variant VARCHAR(32) NOT NULL DEFAULT 'DEFAULT' COMMENT '展示布局子样式';

ALTER TABLE t_album
    ADD COLUMN show_time_and_date TINYINT NOT NULL DEFAULT 0 COMMENT '是否显示时间日期';

UPDATE t_album
SET display_variant = 'DEFAULT'
WHERE display_variant IS NULL OR display_variant = '';

UPDATE t_album
SET show_time_and_date = 0
WHERE show_time_and_date IS NULL;
