DELETE FROM user_follow
WHERE user_id BETWEEN 930001 AND 930003 OR follow_user_id BETWEEN 930001 AND 930003;
DELETE FROM user_interest WHERE user_id BETWEEN 930001 AND 930003;
DELETE FROM view_history
WHERE user_id BETWEEN 930001 AND 930003 OR video_id BETWEEN 931001 AND 931005;
DELETE FROM user_video
WHERE user_id BETWEEN 930001 AND 930003 OR video_id BETWEEN 931001 AND 931005;
DELETE FROM video WHERE id BETWEEN 931001 AND 931005 OR title LIKE 'UC03 E2E %';
DELETE FROM sys_user WHERE id BETWEEN 930001 AND 930003 OR username LIKE 'uc03_e2e_%';

INSERT INTO sys_user (id, username, password, nickname, avatar_url)
VALUES
  (930001, 'uc03_e2e_viewer', 'e2e-password', 'E2E推荐用户', NULL),
  (930002, 'uc03_e2e_followed', 'e2e-password', 'E2E已关注作者', NULL),
  (930003, 'uc03_e2e_other', 'e2e-password', 'E2E其他作者', NULL);

INSERT INTO video (
  id, title, description, play_url, video_url, user_id, category_id, tags, duration,
  status, play_count, like_count, favorite_count, comment_count, created_at, updated_at
)
VALUES
  (931001, 'UC03 E2E 热门推荐', '热门推荐端到端测试视频', 'http://127.0.0.1:18080/e2e/hot.mp4',
   'uc03-e2e-hot', 930003, 1, '热门 科技', 90, 'public', 8000, 0, 0, 0, '2025-01-01 12:00:00', '2025-01-01 12:00:00'),
  (931002, 'UC03 E2E 普通推荐', '普通推荐端到端测试视频', 'http://127.0.0.1:18080/e2e/low.mp4',
   'uc03-e2e-low', 930003, 1, '日常', 80, 'public', 80, 0, 0, 0, '2025-01-01 11:00:00', '2025-01-01 11:00:00'),
  (931003, 'UC03 E2E 私密视频', '不应出现在推荐列表', 'http://127.0.0.1:18080/e2e/private.mp4',
   'uc03-e2e-private', 930003, 1, '私密', 70, 'private', 50000, 5000, 3000, 2000, '2025-01-01 10:00:00', '2025-01-01 10:00:00'),
  (931004, 'UC03 E2E 关注作者作品', '关注作者优先测试视频', 'http://127.0.0.1:18080/e2e/followed.mp4',
   'uc03-e2e-followed', 930002, 2, '日常', 60, 'public', 0, 0, 0, 0, '2025-01-01 09:00:00', '2025-01-01 09:00:00'),
  (931005, 'UC03 E2E 校园音乐', '校园音乐关键词测试视频', 'http://127.0.0.1:18080/e2e/music.mp4',
   'uc03-e2e-music', 930003, 3, '校园 音乐', 120, 'public', 0, 0, 0, 0, '2025-01-01 08:00:00', '2025-01-01 08:00:00');

INSERT INTO user_follow (user_id, follow_user_id, created_at)
VALUES (930001, 930002, NOW());

INSERT INTO user_interest (user_id, tag, score, created_at, updated_at)
VALUES (930001, '音乐', 80, NOW(), NOW());
