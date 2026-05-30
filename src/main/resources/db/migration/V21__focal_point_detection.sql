-- t_album: focal point settings
ALTER TABLE t_album ADD COLUMN focal_point_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE t_album ADD COLUMN focal_point_provider VARCHAR(32) NULL;

-- t_album_media: focal point data
ALTER TABLE t_album_media ADD COLUMN focal_point_x DOUBLE NULL;
ALTER TABLE t_album_media ADD COLUMN focal_point_y DOUBLE NULL;
ALTER TABLE t_album_media ADD COLUMN focal_point_provider VARCHAR(32) NULL;
ALTER TABLE t_album_media ADD COLUMN focal_point_confidence DOUBLE NULL;
ALTER TABLE t_album_media ADD COLUMN focal_point_region_type VARCHAR(20) NULL;
ALTER TABLE t_album_media ADD COLUMN focal_point_region_width DOUBLE NULL;
ALTER TABLE t_album_media ADD COLUMN focal_point_region_height DOUBLE NULL;
ALTER TABLE t_album_media ADD COLUMN focal_point_updated_at DATETIME NULL;

-- Vision LLM model configurations
CREATE TABLE t_vision_llm_config (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT UNSIGNED NOT NULL,
    name              VARCHAR(100) NOT NULL,
    api_endpoint      VARCHAR(500) NOT NULL,
    api_key_encrypted VARCHAR(500) NOT NULL,
    model_name        VARCHAR(100) NOT NULL,
    max_tokens        INT NOT NULL DEFAULT 1024,
    timeout_seconds   INT NOT NULL DEFAULT 30,
    extra_params      TEXT NULL,
    enabled           TINYINT NOT NULL DEFAULT 1,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT NOT NULL DEFAULT 0,
    INDEX idx_vision_llm_config_user_id (user_id)
);
