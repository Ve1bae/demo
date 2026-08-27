-- 测试种子数据（使用 MERGE 实现幂等：多上下文共享同一 H2 内存库时重复执行不报错）
-- 种子用户（id=1，供评论用户信息回填）
MERGE INTO sys_user (id, username, password, nickname, avatar_url) KEY(id)
VALUES (1, 'tester', 'pwd', '测试用户', 'http://localhost/avatar.jpg');

-- 种子视频（id=1，play_count=0 便于断言自增；play_url 作为弹幕关联标识）
MERGE INTO video (id, title, description, cover_url, play_url, user_id, category_id, duration, status, play_count, like_count, favorite_count, comment_count) KEY(id)
VALUES (1, '测试视频', '用于播放/评论/弹幕测试', 'http://localhost/cover.jpg', 'http://localhost/video.mp4', 1, 0, 60, 'PUBLISHED', 0, 0, 0, 0);
