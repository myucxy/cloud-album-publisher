CREATE INDEX idx_distribution_user_status_created ON t_distribution (user_id, status, created_at DESC);

CREATE INDEX idx_distribution_device_dist_device ON t_distribution_device (distribution_id, device_id);
CREATE INDEX idx_distribution_device_dist_group ON t_distribution_device (distribution_id, group_id);

CREATE INDEX idx_device_group_rel_device_group ON t_device_group_rel (device_id, group_id);

CREATE INDEX idx_album_media_album_sort_id ON t_album_media (album_id, sort_order, id);

CREATE INDEX idx_review_record_media_created ON t_review_record (media_id, created_at DESC);
