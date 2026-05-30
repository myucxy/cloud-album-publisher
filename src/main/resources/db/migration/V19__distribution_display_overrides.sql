ALTER TABLE t_distribution
    ADD COLUMN transition_style VARCHAR(20) NULL COMMENT '播放转场覆盖，空表示继承相册' AFTER item_duration,
    ADD COLUMN display_style VARCHAR(20) NULL COMMENT '展示布局覆盖，空表示继承相册' AFTER transition_style,
    ADD COLUMN display_variant VARCHAR(32) NULL COMMENT '展示布局子样式覆盖，空表示继承相册' AFTER display_style,
    ADD COLUMN show_time_and_date TINYINT NULL COMMENT '是否显示时间日期覆盖，空表示继承相册' AFTER display_variant;
