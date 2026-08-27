-- 测试用 H2 建表脚本（MySQL 兼容模式），覆盖 UC-04/05/06 涉及的表
-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(100) NOT NULL,
  nickname VARCHAR(50),
  avatar_url VARCHAR(255),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_username UNIQUE (username)
);

-- 视频表
CREATE TABLE IF NOT EXISTS video (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255),
  description VARCHAR(2000),
  cover_url VARCHAR(500),
  play_url VARCHAR(500),
  user_id BIGINT,
  category_id INT,
  duration INT,
  status VARCHAR(20),
  play_count INT,
  like_count INT,
  favorite_count INT,
  comment_count INT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 弹幕表
CREATE TABLE IF NOT EXISTS danmaku (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  video_url VARCHAR(500) NOT NULL,
  content TEXT NOT NULL,
  color VARCHAR(20) DEFAULT '#ffffff',
  time DOUBLE NOT NULL,
  user_id VARCHAR(100) NOT NULL,
  is_user BOOLEAN DEFAULT FALSE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 评论表
CREATE TABLE IF NOT EXISTS comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  video_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  parent_id BIGINT,
  like_count INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 用户视频关系表
CREATE TABLE IF NOT EXISTS user_video (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  liked BOOLEAN DEFAULT FALSE,
  favorited BOOLEAN DEFAULT FALSE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_video UNIQUE (user_id, video_id)
);
