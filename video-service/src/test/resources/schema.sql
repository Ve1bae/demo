-- H2 内存库测试建表（MySQL 兼容模式；简化了 ON UPDATE，功能与 001_video_schema.sql 一致）
CREATE TABLE IF NOT EXISTS video (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    cover_url VARCHAR(500) NULL,
    play_url VARCHAR(500) NULL,
    author VARCHAR(100) NULL,
    user_id BIGINT NULL,
    category_id INT NULL,
    duration INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'public',
    play_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    video_url VARCHAR(500) NULL,
    url_240p VARCHAR(500) NULL,
    url_360p VARCHAR(500) NULL,
    url_480p VARCHAR(500) NULL,
    url_720p VARCHAR(500) NULL,
    url_1080p VARCHAR(500) NULL,
    default_quality VARCHAR(20) NULL,
    tags VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    username VARCHAR(50) NULL,
    content VARCHAR(500) NOT NULL,
    parent_id BIGINT NULL,
    like_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS comment_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_comment_like UNIQUE (user_id, comment_id)
);

CREATE TABLE IF NOT EXISTS danmaku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    username VARCHAR(50) NULL,
    content VARCHAR(255) NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT '#ffffff',
    time_seconds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS view_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    view_count INT NOT NULL DEFAULT 1,
    progress_seconds INT NOT NULL DEFAULT 0,
    last_viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_view_history UNIQUE (user_id, video_id)
);

CREATE TABLE IF NOT EXISTS user_video (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    liked TINYINT(1) NOT NULL DEFAULT 0,
    favorited TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_video UNIQUE (user_id, video_id)
);
