CREATE TABLE IF NOT EXISTS interaction_dynamic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_interaction_dynamic_created (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interaction_dynamic_mention (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dynamic_id BIGINT NOT NULL,
    mentioned_user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dynamic_mention (dynamic_id, mentioned_user_id),
    KEY idx_dynamic_mention_user (mentioned_user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interaction_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    dynamic_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient (recipient_user_id, is_read, created_at, id),
    KEY idx_notification_dynamic (dynamic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
