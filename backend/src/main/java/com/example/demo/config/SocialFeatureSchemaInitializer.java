package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SocialFeatureSchemaInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SocialFeatureSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public SocialFeatureSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            addColumnIfMissing("sys_user", "avatar_url", "ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(255) DEFAULT NULL");
            addColumnIfMissing("sys_user", "bio", "ALTER TABLE sys_user ADD COLUMN bio VARCHAR(500) DEFAULT NULL");
            addColumnIfMissing("video", "duration", "ALTER TABLE video ADD COLUMN duration VARCHAR(32) DEFAULT NULL");

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS user_follow (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "user_id BIGINT NOT NULL," +
                            "follow_user_id BIGINT NOT NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY uk_user_follow_user_target (user_id, follow_user_id)," +
                            "KEY idx_user_follow_user_id (user_id)," +
                            "KEY idx_user_follow_target_id (follow_user_id)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            addColumnIfMissing("user_follow", "user_id", "ALTER TABLE user_follow ADD COLUMN user_id BIGINT DEFAULT NULL");
            addColumnIfMissing("user_follow", "follow_user_id", "ALTER TABLE user_follow ADD COLUMN follow_user_id BIGINT DEFAULT NULL");
            addColumnIfMissing("user_follow", "created_at", "ALTER TABLE user_follow ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
            migrateLegacyUserFollowColumns();

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS view_history (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "user_id BIGINT NOT NULL," +
                            "video_id BIGINT NOT NULL," +
                            "view_count INT NOT NULL DEFAULT 1," +
                            "progress_seconds INT NOT NULL DEFAULT 0," +
                            "last_viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY uk_view_history_user_video (user_id, video_id)," +
                            "KEY idx_view_history_user_id (user_id)," +
                            "KEY idx_view_history_video_id (video_id)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            addColumnIfMissing("view_history", "user_id", "ALTER TABLE view_history ADD COLUMN user_id BIGINT DEFAULT NULL");
            addColumnIfMissing("view_history", "video_id", "ALTER TABLE view_history ADD COLUMN video_id BIGINT DEFAULT NULL");
            addColumnIfMissing("view_history", "view_count", "ALTER TABLE view_history ADD COLUMN view_count INT NOT NULL DEFAULT 1");
            addColumnIfMissing("view_history", "progress_seconds", "ALTER TABLE view_history ADD COLUMN progress_seconds INT NOT NULL DEFAULT 0");
            addColumnIfMissing("view_history", "last_viewed_at", "ALTER TABLE view_history ADD COLUMN last_viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS tag (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(64) NOT NULL," +
                            "creator_user_id BIGINT DEFAULT NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY uk_tag_name (name)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            addColumnIfMissing("tag", "creator_user_id", "ALTER TABLE tag ADD COLUMN creator_user_id BIGINT DEFAULT NULL");
            addColumnIfMissing("tag", "created_at", "ALTER TABLE tag ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS video_tag (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "video_id BIGINT NOT NULL," +
                            "tag_id BIGINT NOT NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY uk_video_tag_video_tag (video_id, tag_id)," +
                            "KEY idx_video_tag_video_id (video_id)," +
                            "KEY idx_video_tag_tag_id (tag_id)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            addColumnIfMissing("video_tag", "video_id", "ALTER TABLE video_tag ADD COLUMN video_id BIGINT DEFAULT NULL");
            addColumnIfMissing("video_tag", "tag_id", "ALTER TABLE video_tag ADD COLUMN tag_id BIGINT DEFAULT NULL");
            addColumnIfMissing("video_tag", "created_at", "ALTER TABLE video_tag ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS notification (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "user_id BIGINT NOT NULL," +
                            "type VARCHAR(32) NOT NULL," +
                            "content VARCHAR(500) NOT NULL," +
                            "related_user_id BIGINT DEFAULT NULL," +
                            "related_video_id BIGINT DEFAULT NULL," +
                            "related_comment_id BIGINT DEFAULT NULL," +
                            "is_read TINYINT(1) NOT NULL DEFAULT 0," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "KEY idx_notification_user_id (user_id)," +
                            "KEY idx_notification_is_read (is_read)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            addColumnIfMissing("notification", "user_id", "ALTER TABLE notification ADD COLUMN user_id BIGINT DEFAULT NULL");
            addColumnIfMissing("notification", "type", "ALTER TABLE notification ADD COLUMN type VARCHAR(32) DEFAULT NULL");
            addColumnIfMissing("notification", "content", "ALTER TABLE notification ADD COLUMN content VARCHAR(500) DEFAULT NULL");
            addColumnIfMissing("notification", "related_user_id", "ALTER TABLE notification ADD COLUMN related_user_id BIGINT DEFAULT NULL");
            addColumnIfMissing("notification", "related_video_id", "ALTER TABLE notification ADD COLUMN related_video_id BIGINT DEFAULT NULL");
            addColumnIfMissing("notification", "related_comment_id", "ALTER TABLE notification ADD COLUMN related_comment_id BIGINT DEFAULT NULL");
            addColumnIfMissing("notification", "is_read", "ALTER TABLE notification ADD COLUMN is_read TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing("notification", "created_at", "ALTER TABLE notification ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        } catch (Exception e) {
            log.warn("Skip social feature schema auto update: {}", e.getMessage());
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void migrateLegacyUserFollowColumns() {
        if (columnExists("user_follow", "follower_id")) {
            jdbcTemplate.execute("UPDATE user_follow SET user_id = follower_id WHERE user_id IS NULL AND follower_id IS NOT NULL");
            makeColumnNullable("user_follow", "follower_id");
        }
        if (columnExists("user_follow", "followee_id")) {
            jdbcTemplate.execute("UPDATE user_follow SET follow_user_id = followee_id WHERE follow_user_id IS NULL AND followee_id IS NOT NULL");
            makeColumnNullable("user_follow", "followee_id");
        }
        if (columnExists("user_follow", "following_id")) {
            jdbcTemplate.execute("UPDATE user_follow SET follow_user_id = following_id WHERE follow_user_id IS NULL AND following_id IS NOT NULL");
            makeColumnNullable("user_follow", "following_id");
        }
    }

    private void makeColumnNullable(String tableName, String columnName) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " BIGINT NULL DEFAULT NULL");
        } catch (Exception e) {
            log.warn("Skip making {}.{} nullable: {}", tableName, columnName, e.getMessage());
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }
}
