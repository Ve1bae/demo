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
        } catch (Exception e) {
            log.warn("Skip live_room schema auto update: {}", e.getMessage());
        }
    }
}
