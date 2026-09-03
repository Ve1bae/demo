SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS video_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE video_db;

CREATE TABLE IF NOT EXISTS video (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    cover_url VARCHAR(500),
    play_url VARCHAR(500),
    video_url VARCHAR(500),
    author VARCHAR(255),
    user_id BIGINT,
    category_id INT,
    tags VARCHAR(500),
    duration INT,
    status VARCHAR(20) NOT NULL DEFAULT 'public',
    play_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_video_status_category (status, category_id),
    INDEX idx_video_created (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS view_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    view_count INT NOT NULL DEFAULT 1,
    progress_seconds INT NOT NULL DEFAULT 0,
    last_viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_view_history_user_video (user_id, video_id),
    INDEX idx_view_history_user (user_id, last_viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



