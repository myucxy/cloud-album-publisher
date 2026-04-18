CREATE TABLE t_album_bgm (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    album_id           BIGINT NOT NULL,
    media_id           BIGINT,
    source_id          BIGINT,
    source_type        VARCHAR(20),
    source_name        VARCHAR(100),
    external_media_key VARCHAR(255),
    file_path          VARCHAR(500),
    file_name          VARCHAR(255),
    content_type       VARCHAR(100),
    media_type         VARCHAR(20),
    sort_order         INT DEFAULT 0,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_album_bgm_media (album_id, media_id),
    UNIQUE KEY uk_album_bgm_external (album_id, external_media_key),
    KEY idx_album_bgm_album_sort (album_id, sort_order, id)
);

INSERT INTO t_album_bgm (
    album_id,
    media_id,
    source_id,
    source_type,
    source_name,
    external_media_key,
    file_path,
    file_name,
    content_type,
    media_type,
    sort_order,
    created_at,
    updated_at
)
SELECT
    id,
    bgm_media_id,
    bgm_source_id,
    bgm_source_type,
    bgm_source_name,
    bgm_external_media_key,
    bgm_path,
    bgm_file_name,
    bgm_content_type,
    bgm_media_type,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM t_album
WHERE bgm_media_id IS NOT NULL OR bgm_external_media_key IS NOT NULL;
