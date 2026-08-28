CREATE TABLE IF NOT EXISTS live_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    title VARCHAR(100) NOT NULL,
    stream_name VARCHAR(100) NOT NULL UNIQUE,
    push_url VARCHAR(500) NOT NULL,
    play_url VARCHAR(500) NOT NULL,
    cover_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_live_room_status_created (status, create_time, id)
);

CREATE TABLE IF NOT EXISTS live_danmu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    username VARCHAR(50) NOT NULL,
    content VARCHAR(255) NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT '#ffffff',
    send_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_live_danmu_room_time (room_id, send_time, id)
);

CREATE TABLE IF NOT EXISTS room_likes (
    room_id BIGINT PRIMARY KEY,
    like_count BIGINT NOT NULL DEFAULT 0
);
