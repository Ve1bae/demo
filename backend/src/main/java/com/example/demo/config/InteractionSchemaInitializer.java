package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class InteractionSchemaInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(InteractionSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public InteractionSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS comment_like (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "comment_id BIGINT NOT NULL," +
                            "user_id BIGINT NOT NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY uk_comment_like_comment_user (comment_id, user_id)," +
                            "KEY idx_comment_like_comment_id (comment_id)," +
                            "KEY idx_comment_like_user_id (user_id)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            ensureCommentColumn("parent_id", "ALTER TABLE comment ADD COLUMN parent_id BIGINT NULL");
            ensureCommentColumn("like_count", "ALTER TABLE comment ADD COLUMN like_count INT NOT NULL DEFAULT 0");
        } catch (Exception e) {
            log.warn("Skip interaction schema auto update: {}", e.getMessage());
        }
    }

    private void ensureCommentColumn(String column, String alterSql) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comment' AND COLUMN_NAME = ?",
                    Integer.class,
                    column
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception e) {
            log.warn("Skip comment column check {}: {}", column, e.getMessage());
        }
    }
}
