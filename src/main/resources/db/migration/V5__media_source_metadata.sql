ALTER TABLE t_media
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源类型',
    ADD COLUMN source_id BIGINT NULL COMMENT '外部来源ID',
    ADD COLUMN source_name VARCHAR(100) NOT NULL DEFAULT '上传' COMMENT '来源名称',
    ADD COLUMN folder_path VARCHAR(500) NOT NULL DEFAULT '/上传' COMMENT '逻辑目录路径',
    ADD COLUMN origin_uri VARCHAR(1000) NULL COMMENT '来源定位信息',
    ADD COLUMN ingest_mode VARCHAR(20) NOT NULL DEFAULT 'UPLOADED' COMMENT '导入方式';

UPDATE t_media
SET source_type = 'UPLOAD'
WHERE source_type IS NULL OR source_type = '';

UPDATE t_media
SET source_name = '上传'
WHERE source_name IS NULL OR source_name = '';

UPDATE t_media
SET folder_path = '/上传'
WHERE folder_path IS NULL OR folder_path = '';

UPDATE t_media
SET ingest_mode = 'UPLOADED'
WHERE ingest_mode IS NULL OR ingest_mode = '';

UPDATE t_media
SET origin_uri = object_key
WHERE (origin_uri IS NULL OR origin_uri = '')
  AND object_key IS NOT NULL
  AND object_key <> '';

ALTER TABLE t_media
    ADD INDEX idx_media_source_type (source_type),
    ADD INDEX idx_media_source_id (source_id),
    ADD INDEX idx_media_folder_path (folder_path);
