ALTER TABLE t_album
    ADD COLUMN transition_style VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '播放转场样式';

UPDATE t_album
SET transition_style = 'NONE'
WHERE transition_style IS NULL OR transition_style = '';
