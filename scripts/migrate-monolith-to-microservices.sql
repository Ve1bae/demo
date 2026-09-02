-- Hangyin 单体库 -> 微服务数据库迁移脚本
-- 仅编写/审核脚本时使用；本文件不会被应用自动执行。
-- 使用前请备份 hangyin_video、user_db、video_db、live_db。
-- 执行方式示例：mysql -uroot -p < scripts/migrate-monolith-to-microservices.sql

SET NAMES utf8mb4;
SET @source_db = 'hangyin_video';

CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS video_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS live_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 用户服务拥有：用户、关注、兴趣偏好
INSERT INTO user_db.sys_user (id, username, password, nickname, avatar_url, bio, create_time)
SELECT id, username, password, nickname, avatar_url, NULL, create_time
FROM hangyin_video.sys_user
ON DUPLICATE KEY UPDATE username=VALUES(username), password=VALUES(password),
  nickname=VALUES(nickname), avatar_url=VALUES(avatar_url), create_time=VALUES(create_time);

INSERT INTO user_db.user_follow (id, user_id, follow_user_id, created_at)
SELECT id, user_id, follow_user_id, created_at FROM hangyin_video.user_follow
ON DUPLICATE KEY UPDATE created_at=VALUES(created_at);

INSERT INTO user_db.user_interest (id, user_id, tag, score, created_at, updated_at)
SELECT id, user_id, tag, score, created_at, updated_at FROM hangyin_video.user_interest
ON DUPLICATE KEY UPDATE score=VALUES(score), updated_at=VALUES(updated_at);

-- 视频服务拥有：视频、评论、视频互动、弹幕、观看记录、评论点赞
INSERT INTO video_db.video (id, title, description, cover_url, play_url, author, user_id, category_id, duration,
  status, play_count, like_count, favorite_count, comment_count, video_url, url_240p, url_360p, url_480p,
  url_720p, url_1080p, default_quality, tags, created_at, updated_at)
SELECT id, title, description, cover_url, play_url, author, user_id, category_id, duration, status,
  play_count, like_count, favorite_count, comment_count, video_url, url_240p, url_360p, url_480p,
  url_720p, url_1080p, default_quality, tags, created_at, updated_at
FROM hangyin_video.video
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description), cover_url=VALUES(cover_url),
  play_url=VALUES(play_url), author=VALUES(author), user_id=VALUES(user_id), status=VALUES(status),
  play_count=VALUES(play_count), like_count=VALUES(like_count), favorite_count=VALUES(favorite_count),
  comment_count=VALUES(comment_count), url_240p=VALUES(url_240p), url_360p=VALUES(url_360p),
  url_480p=VALUES(url_480p), url_720p=VALUES(url_720p), url_1080p=VALUES(url_1080p),
  default_quality=VALUES(default_quality), tags=VALUES(tags), updated_at=VALUES(updated_at);

INSERT INTO video_db.comment (id, video_id, user_id, username, content, parent_id, like_count, created_at)
SELECT c.id, c.video_id, c.user_id, COALESCE(u.nickname, u.username, CONCAT('用户 ', c.user_id)),
  c.content, c.parent_id, c.like_count, c.created_at
FROM hangyin_video.comment c LEFT JOIN hangyin_video.sys_user u ON u.id=c.user_id
ON DUPLICATE KEY UPDATE content=VALUES(content), username=VALUES(username), like_count=VALUES(like_count);

INSERT INTO video_db.user_video (id, user_id, video_id, liked, favorited, created_at, updated_at)
SELECT id, user_id, video_id, liked, favorited, created_at, updated_at FROM hangyin_video.user_video
ON DUPLICATE KEY UPDATE liked=VALUES(liked), favorited=VALUES(favorited), updated_at=VALUES(updated_at);

INSERT INTO video_db.view_history (id, user_id, video_id, view_count, progress_seconds, last_viewed_at)
SELECT id, user_id, video_id, view_count, progress_seconds, last_viewed_at FROM hangyin_video.view_history
ON DUPLICATE KEY UPDATE view_count=VALUES(view_count), progress_seconds=VALUES(progress_seconds), last_viewed_at=VALUES(last_viewed_at);

INSERT INTO video_db.comment_like (id, user_id, comment_id, created_at)
SELECT id, user_id, comment_id, created_at FROM hangyin_video.comment_like
ON DUPLICATE KEY UPDATE created_at=VALUES(created_at);

INSERT INTO video_db.danmaku (id, video_id, user_id, username, content, color, time_seconds, created_at)
SELECT d.id, v.id, NULLIF(d.user_id, ''), COALESCE(u.nickname, u.username, d.user_id), d.content,
  COALESCE(d.color, '#ffffff'), ROUND(d.time), d.created_at
FROM hangyin_video.danmaku d
JOIN hangyin_video.video v ON v.video_url=d.video_url
LEFT JOIN hangyin_video.sys_user u ON u.id=CAST(NULLIF(d.user_id, '') AS UNSIGNED)
ON DUPLICATE KEY UPDATE content=VALUES(content), color=VALUES(color), time_seconds=VALUES(time_seconds);

-- 直播服务拥有：直播间、直播点赞计数、直播弹幕
INSERT INTO live_db.live_room (id, user_id, category_id, title, stream_name, push_url, play_url, cover_url, status, create_time)
SELECT id, user_id, category_id, title, stream_name, push_url, play_url, cover_url, status, create_time
FROM hangyin_video.live_room
ON DUPLICATE KEY UPDATE title=VALUES(title), push_url=VALUES(push_url), play_url=VALUES(play_url),
  cover_url=VALUES(cover_url), status=VALUES(status);

INSERT INTO live_db.room_likes (room_id, like_count)
SELECT room_id, like_count FROM hangyin_video.room_likes
ON DUPLICATE KEY UPDATE like_count=VALUES(like_count);

INSERT INTO live_db.live_danmu (id, room_id, user_id, username, content, color, send_time)
SELECT id, room_id, user_id, COALESCE(username, CONCAT('用户 ', user_id)), content,
  COALESCE(color, '#ffffff'), send_time FROM hangyin_video.live_danmu
ON DUPLICATE KEY UPDATE content=VALUES(content), color=VALUES(color), send_time=VALUES(send_time);

-- 校验：三组数量应与源库对应表一致（弹幕表按 video_url 可匹配的视频统计）。
SELECT 'users' AS item, (SELECT COUNT(*) FROM hangyin_video.sys_user) source_count,
       (SELECT COUNT(*) FROM user_db.sys_user) target_count
UNION ALL SELECT 'videos', (SELECT COUNT(*) FROM hangyin_video.video), (SELECT COUNT(*) FROM video_db.video)
UNION ALL SELECT 'live_rooms', (SELECT COUNT(*) FROM hangyin_video.live_room), (SELECT COUNT(*) FROM live_db.live_room)
UNION ALL SELECT 'comments', (SELECT COUNT(*) FROM hangyin_video.comment), (SELECT COUNT(*) FROM video_db.comment);
