package com.example.video.recommendation;

import com.example.video.client.UserPreferenceClient;
import com.example.video.model.UserPreference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:video_recommendation;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql",
        "user-service.enabled=false"
})
@AutoConfigureMockMvc
class RecommendationApiH2Test {
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        @Primary
        UserPreferenceClient preferenceClient() {
            return mock(UserPreferenceClient.class);
        }
    }

    @BeforeEach
    void setUp() {
        clean();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void guestGetsPublicVideosByScoreAndExcludesPrivateVideo() throws Exception {
        insertVideo("low", 10L, 1, "public", 10, 0, 0, 0, "daily", BASE_TIME);
        insertVideo("high", 11L, 1, "public", 1000, 20, 10, 8, "tech", BASE_TIME.minusDays(1));
        insertVideo("private", 12L, 1, "private", 99999, 99, 99, 99, "hidden", BASE_TIME);

        mockMvc.perform(get("/api/videos/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].title", is("high")))
                .andExpect(jsonPath("$.data[1].title", is("low")));
    }

    @Test
    void filtersByCategoryAndKeyword() throws Exception {
        insertVideo("campus_music", 11L, 3, "public", 0, 0, 0, 0, "campus music", BASE_TIME);
        insertVideo("campus_sport", 11L, 3, "public", 0, 0, 0, 0, "sport", BASE_TIME);
        insertVideo("other_category", 11L, 4, "public", 0, 0, 0, 0, "campus music", BASE_TIME);

        mockMvc.perform(get("/api/videos/recommend").param("categoryId", "3").param("keyword", "music"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title", is("campus_music")));
    }

    @Test
    void localViewHistoryLowersViewedVideo() throws Exception {
        long viewed = insertVideo("viewed", 11L, 1, "public", 100, 0, 0, 0, "daily", BASE_TIME);
        insertVideo("unseen", 12L, 1, "public", 100, 0, 0, 0, "daily", BASE_TIME);
        jdbcTemplate.update("INSERT INTO view_history (user_id, video_id) VALUES (?, ?)", 42L, viewed);

        mockMvc.perform(get("/api/videos/recommend").header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title", is("unseen")))
                .andExpect(jsonPath("$.data[1].title", is("viewed")));
    }

    @Test
    void returnsRequestedPage() throws Exception {
        insertVideo("rank_1", 11L, 1, "public", 8000, 0, 0, 0, "one", BASE_TIME);
        insertVideo("rank_2", 11L, 1, "public", 4000, 0, 0, 0, "two", BASE_TIME.minusMinutes(1));
        insertVideo("rank_3", 11L, 1, "public", 80, 0, 0, 0, "three", BASE_TIME.minusMinutes(2));

        mockMvc.perform(get("/api/videos/recommend").param("page", "2").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title", is("rank_2")));
    }

    @Test
    void returnsEmptyForNoMatch() throws Exception {
        insertVideo("existing", 11L, 1, "public", 0, 0, 0, 0, "daily", BASE_TIME);

        mockMvc.perform(get("/api/videos/recommend").param("keyword", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void rejectsMalformedPagingAndUserHeaderAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/videos/recommend").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)));
        mockMvc.perform(get("/api/videos/recommend").header("X-User-Id", "guest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)));
    }

    private long insertVideo(String title, long userId, int categoryId, String status,
                             int playCount, int likeCount, int favoriteCount, int commentCount,
                             String tags, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO video (title, description, play_url, user_id, category_id, tags, status, "
                        + "play_count, like_count, favorite_count, comment_count, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                title, "UC-03 H2 test data", "https://example.test/" + title + ".mp4", userId,
                categoryId, tags, status, playCount, likeCount, favoriteCount, commentCount,
                Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
        return jdbcTemplate.queryForObject("SELECT id FROM video WHERE title = ?", Long.class, title);
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM view_history");
        jdbcTemplate.update("DELETE FROM user_video");
        jdbcTemplate.update("DELETE FROM video");
    }
}



