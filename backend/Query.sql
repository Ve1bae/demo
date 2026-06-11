CREATE TABLE live_room (
                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                           user_id BIGINT NOT NULL,
                           title VARCHAR(100) NOT NULL,
                           stream_name VARCHAR(100) NOT NULL UNIQUE,
                           push_url VARCHAR(255) NOT NULL,
                           play_url VARCHAR(255) NOT NULL,
                           cover_url VARCHAR(255) DEFAULT NULL,
                           status VARCHAR(20) NOT NULL DEFAULT 'offline',
                           create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
