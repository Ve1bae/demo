-- 测试种子数据（MySQL，使用 ON DUPLICATE KEY UPDATE 实现幂等：CI 每次建空表后导入可重复执行）
-- 种子用户（id=1，供评论/弹幕用户信息回填）
INSERT INTO sys_user (id, username, password, nickname, avatar_url) VALUES
  (1, 'tester', 'pwd', '测试用户', 'http://localhost/avatar.jpg')
  ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    avatar_url = VALUES(avatar_url);

-- 种子视频（id=1，play_count=0 便于断言自增；play_url 作为弹幕关联标识）
INSERT INTO video (id, title, description, cover_url, play_url, user_id, category_id, duration, status, play_count, like_count, favorite_count, comment_count) VALUES
  (1, '测试视频', '用于播放/评论/弹幕测试', 'http://localhost/cover.jpg', 'http://localhost/video.mp4', 1, 0, 60, 'public', 0, 0, 0, 0)
  ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    play_url = VALUES(play_url),
    play_count = VALUES(play_count),
    like_count = VALUES(like_count),
    favorite_count = VALUES(favorite_count),
    comment_count = VALUES(comment_count);
