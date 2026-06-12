package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LiveRoomSchemaInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LiveRoomSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public LiveRoomSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            ensureUserSchema();
            ensureVideoSchema();

            Integer coverUrlCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'live_room' AND column_name = 'cover_url'",
                    Integer.class);

            if (coverUrlCount == null || coverUrlCount == 0) {
                jdbcTemplate.execute("ALTER TABLE live_room ADD COLUMN cover_url VARCHAR(255) DEFAULT NULL");
            }

            Integer categoryIdCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'live_room' AND column_name = 'category_id'",
                    Integer.class);

            if (categoryIdCount == null || categoryIdCount == 0) {
                jdbcTemplate.execute("ALTER TABLE live_room ADD COLUMN category_id BIGINT DEFAULT NULL");
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS room_likes (" +
                    "room_id BIGINT NOT NULL, " +
                    "like_count BIGINT NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (room_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS live_danmu (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, " +
                    "room_id BIGINT NOT NULL, " +
                    "user_id BIGINT DEFAULT NULL, " +
                    "username VARCHAR(50) DEFAULT NULL, " +
                    "content VARCHAR(255) NOT NULL, " +
                    "color VARCHAR(20) DEFAULT '#ffffff', " +
                    "send_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (id), " +
                    "KEY idx_live_danmu_room_time (room_id, send_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("Skip live schema auto update: {}", e.getMessage());
        }
    }

    private void ensureUserSchema() {
        addColumnIfMissing("sys_user", "avatar_url", "ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(500) DEFAULT NULL");
    }

    private void ensureVideoSchema() {
        addColumnIfMissing("video", "video_url", "ALTER TABLE video ADD COLUMN video_url VARCHAR(500) DEFAULT NULL COMMENT 'Original video URL'");
        addColumnIfMissing("video", "url_240p", "ALTER TABLE video ADD COLUMN url_240p VARCHAR(500) DEFAULT NULL");
        addColumnIfMissing("video", "url_360p", "ALTER TABLE video ADD COLUMN url_360p VARCHAR(500) DEFAULT NULL");
        addColumnIfMissing("video", "url_480p", "ALTER TABLE video ADD COLUMN url_480p VARCHAR(500) DEFAULT NULL");
        addColumnIfMissing("video", "url_720p", "ALTER TABLE video ADD COLUMN url_720p VARCHAR(500) DEFAULT NULL");
        addColumnIfMissing("video", "url_1080p", "ALTER TABLE video ADD COLUMN url_1080p VARCHAR(500) DEFAULT NULL");
        addColumnIfMissing("video", "default_quality", "ALTER TABLE video ADD COLUMN default_quality VARCHAR(20) DEFAULT NULL");
        addColumnIfMissing("video", "user_id", "ALTER TABLE video ADD COLUMN user_id BIGINT DEFAULT NULL");
        addColumnIfMissing("video", "category_id", "ALTER TABLE video ADD COLUMN category_id INT DEFAULT NULL");
        addColumnIfMissing("video", "duration", "ALTER TABLE video ADD COLUMN duration INT DEFAULT NULL");
        addColumnIfMissing("video", "status", "ALTER TABLE video ADD COLUMN status VARCHAR(20) DEFAULT NULL");
        addColumnIfMissing("video", "updated_at", "ALTER TABLE video ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        addIndexIfMissing("video", "idx_video_video_url", "CREATE INDEX idx_video_video_url ON video (video_url)");
        jdbcTemplate.update("UPDATE video SET status = 'public' WHERE status IS NULL OR status = ''");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_video (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, " +
                "user_id BIGINT NOT NULL, " +
                "video_id BIGINT NOT NULL, " +
                "liked TINYINT(1) DEFAULT 0, " +
                "favorited TINYINT(1) DEFAULT 0, " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (id), " +
                "UNIQUE KEY uk_user_video (user_id, video_id), " +
                "KEY idx_user_video_user_id (user_id), " +
                "KEY idx_user_video_video_id (video_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName);

        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                tableName,
                indexName);

        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }
}

