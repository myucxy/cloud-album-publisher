-- 订正历史 SMB 数据：将 share_name 合并到 root_path/bound_path 中
UPDATE t_media_source
SET root_path = CASE
        WHEN root_path IS NULL OR root_path = '' OR root_path = '/' THEN CONCAT('/', TRIM(BOTH '/' FROM share_name))
        ELSE CONCAT('/', TRIM(BOTH '/' FROM share_name), '/', TRIM(LEADING '/' FROM root_path))
    END,
    bound_path = CASE
        WHEN bound_path IS NULL OR bound_path = '' THEN bound_path
        WHEN bound_path = '/' THEN CONCAT('/', TRIM(BOTH '/' FROM share_name))
        ELSE CONCAT('/', TRIM(BOTH '/' FROM share_name), '/', TRIM(LEADING '/' FROM bound_path))
    END,
    share_name = ''
WHERE source_type = 'SMB'
  AND share_name IS NOT NULL
  AND share_name <> ''
  AND (
      root_path IS NULL
      OR root_path = ''
      OR root_path = '/'
      OR root_path NOT LIKE CONCAT('/', TRIM(BOTH '/' FROM share_name), '%')
  );
