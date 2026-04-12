ALTER TABLE t_media_source
    ADD COLUMN config_json TEXT NULL COMMENT '媒体源配置JSON',
    ADD COLUMN credential_ciphertext TEXT NULL COMMENT '媒体源凭证密文',
    ADD COLUMN bound_path VARCHAR(500) NULL COMMENT '绑定目录',
    ADD COLUMN bound_path_name VARCHAR(255) NULL COMMENT '绑定目录显示名';

UPDATE t_media_source
SET bound_path = CASE
    WHEN bound_path IS NULL OR bound_path = '' THEN CASE
        WHEN root_path IS NULL OR root_path = '' THEN '/'
        ELSE root_path
    END
    ELSE bound_path
END;

UPDATE t_media_source
SET bound_path_name = CASE
    WHEN bound_path_name IS NULL OR bound_path_name = '' THEN name
    ELSE bound_path_name
END;
