CREATE TABLE IF NOT EXISTS interaction_dynamic (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interaction_dynamic_mention (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dynamic_id BIGINT NOT NULL,
    mentioned_user_id BIGINT NOT NULL,
    UNIQUE KEY uk_dynamic_mention (dynamic_id, mentioned_user_id)
);

CREATE TABLE IF NOT EXISTS interaction_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    dynamic_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL
);

CREATE INDEX idx_interaction_dynamic_created ON interaction_dynamic (created_at, id);
CREATE INDEX idx_interaction_notification_recipient ON interaction_notification (recipient_user_id, is_read, created_at, id);
