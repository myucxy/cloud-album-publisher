ALTER TABLE t_album
    ADD COLUMN display_style VARCHAR(20) NOT NULL DEFAULT 'SINGLE' COMMENT '展示布局样式';

UPDATE t_album
SET display_style = 'SINGLE'
WHERE display_style IS NULL OR display_style = '';
