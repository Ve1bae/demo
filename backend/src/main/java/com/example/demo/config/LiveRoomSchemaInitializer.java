package com.example.demo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LiveRoomSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public LiveRoomSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'live_room' AND column_name = 'cover_url'",
                Integer.class);

        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE live_room ADD COLUMN cover_url VARCHAR(255) DEFAULT NULL");
        }
    }
}
