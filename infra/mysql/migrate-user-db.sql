-- One-time/idempotent migration for environments that already have hangyin_video.
-- The INSERT ... SELECT statements intentionally copy only user-service-owned tables.
CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @has_bio = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'hangyin_video' AND table_name = 'sys_user' AND column_name = 'bio'
);
SET @sql = IF(@has_bio = 0,
  'ALTER TABLE hangyin_video.sys_user ADD COLUMN bio VARCHAR(500) DEFAULT NULL',
  'SELECT 1');
PREPARE stmt_bio FROM @sql; EXECUTE stmt_bio; DEALLOCATE PREPARE stmt_bio;

INSERT INTO user_db.sys_user (id, username, password, nickname, avatar_url, bio, create_time)
SELECT id, username, password, nickname, avatar_url,
       COALESCE(bio, NULL), create_time
FROM hangyin_video.sys_user
ON DUPLICATE KEY UPDATE
  username = VALUES(username), password = VALUES(password), nickname = VALUES(nickname),
  avatar_url = VALUES(avatar_url), bio = VALUES(bio);

INSERT INTO user_db.user_follow (id, user_id, follow_user_id, created_at)
SELECT id, user_id, follow_user_id, created_at
FROM hangyin_video.user_follow
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

INSERT INTO user_db.user_interest (id, user_id, tag, score, created_at, updated_at)
SELECT id, user_id, tag, score, created_at, updated_at
FROM hangyin_video.user_interest
ON DUPLICATE KEY UPDATE score = VALUES(score), updated_at = VALUES(updated_at);

-- Keep AUTO_INCREMENT values ahead of copied identifiers.
SET @next_user_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM user_db.sys_user);
SET @sql = CONCAT('ALTER TABLE user_db.sys_user AUTO_INCREMENT = ', @next_user_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
