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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS view_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    view_count INT NOT NULL DEFAULT 1,
    progress_seconds INT NOT NULL DEFAULT 0,
    last_viewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, video_id)
);
