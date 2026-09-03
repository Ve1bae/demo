USE user_db;

DELETE FROM user_follow WHERE user_id BETWEEN 930001 AND 930004 OR follow_user_id BETWEEN 930001 AND 930004;
DELETE FROM user_interest WHERE user_id BETWEEN 930001 AND 930004;
DELETE FROM sys_user WHERE id BETWEEN 930001 AND 930004 OR username LIKE 'uc03_e2e_%';

INSERT INTO sys_user (id, username, password, nickname, avatar_url)
VALUES
  (1, 'e2e_browser_user', 'e2e-password', '测试用户', NULL),
  (930001, 'uc03_e2e_viewer', 'e2e-password', 'E2E推荐用户', NULL),
  (930002, 'uc03_e2e_followed', 'e2e-password', 'E2E已关注作者', NULL),
  (930003, 'uc03_e2e_other', 'e2e-password', 'E2E其他作者', NULL),
  (930004, 'uc07_e2e_creator', 'e2e-password', 'UC07页面主播', NULL)
ON DUPLICATE KEY UPDATE
  username = VALUES(username), password = VALUES(password), nickname = VALUES(nickname), avatar_url = VALUES(avatar_url);

INSERT INTO user_follow (user_id, follow_user_id, created_at)
VALUES (930001, 930002, NOW());

INSERT INTO user_interest (user_id, tag, score, created_at, updated_at)
VALUES (930001, '音乐', 80, NOW(), NOW());
