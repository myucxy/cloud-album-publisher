USE cloud_album;

INSERT INTO t_review_setting (id, auto_approve_enabled)
VALUES (1, 1)
ON DUPLICATE KEY UPDATE auto_approve_enabled = 1;

UPDATE t_review_record rr
INNER JOIN t_media m ON m.id = rr.media_id
SET rr.status = 'APPROVED',
    rr.reject_reason = NULL,
    rr.reviewed_at = COALESCE(rr.reviewed_at, NOW()),
    rr.updated_at = NOW()
WHERE rr.status = 'PENDING'
  AND m.status = 'READY';

INSERT INTO t_review_record (media_id, user_id, status, reviewed_at, created_at, updated_at)
SELECT m.id, m.user_id, 'APPROVED', NOW(), NOW(), NOW()
FROM t_media m
LEFT JOIN t_review_record rr ON rr.media_id = m.id
WHERE m.status = 'READY'
  AND rr.id IS NULL;
