USE video_db;

DELETE FROM comment_like WHERE comment_id IN (SELECT id FROM comment WHERE video_id BETWEEN 931001 AND 931005);
DELETE FROM comment WHERE video_id BETWEEN 931001 AND 931005;
DELETE FROM danmaku WHERE video_id BETWEEN 931001 AND 931005;
DELETE FROM view_history WHERE video_id BETWEEN 931001 AND 931005 OR user_id BETWEEN 930001 AND 930004;
DELETE FROM user_video WHERE video_id BETWEEN 931001 AND 931005 OR user_id BETWEEN 930001 AND 930004;
DELETE FROM video WHERE id BETWEEN 931001 AND 931005 OR title LIKE 'UC03 E2E %';

INSERT INTO video (
  id, title, description, play_url, video_url, cover_url, author, user_id, category_id, tags,
  duration, status, play_count, like_count, favorite_count, comment_count, created_at, updated_at
)
VALUES
  (931001, 'UC03 E2E 热门推荐', '热门推荐端到端测试视频', 'http://127.0.0.1:5173/e2e/hot.webm',
   'uc03-e2e-hot', 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==',
   'E2E其他作者', 930003, 1, '热门 科技', 90, 'public', 8000, 0, 0, 0, '2025-01-01 12:00:00', '2025-01-01 12:00:00'),
  (931002, 'UC03 E2E 普通推荐', '普通推荐端到端测试视频', 'http://127.0.0.1:5173/e2e/hot.webm',
   'uc03-e2e-low', '', 'E2E其他作者', 930003, 1, '日常', 80, 'public', 80, 0, 0, 0, '2025-01-01 11:00:00', '2025-01-01 11:00:00'),
  (931003, 'UC03 E2E 私密视频', '不应出现在推荐列表', 'http://127.0.0.1:5173/e2e/hot.webm',
   'uc03-e2e-private', '', 'E2E其他作者', 930003, 1, '私密', 70, 'private', 50000, 5000, 3000, 2000, '2025-01-01 10:00:00', '2025-01-01 10:00:00'),
  (931004, 'UC03 E2E 关注作者作品', '关注作者优先测试视频', 'http://127.0.0.1:5173/e2e/hot.webm',
   'uc03-e2e-followed', '', 'E2E已关注作者', 930002, 2, '日常', 60, 'public', 0, 0, 0, 0, '2025-01-01 09:00:00', '2025-01-01 09:00:00'),
  (931005, 'UC03 E2E 校园音乐', '校园音乐关键词测试视频', 'http://127.0.0.1:5173/e2e/hot.webm',
   'uc03-e2e-music', '', 'E2E其他作者', 930003, 3, '校园 音乐', 120, 'public', 0, 0, 0, 0, '2025-01-01 08:00:00', '2025-01-01 08:00:00');
