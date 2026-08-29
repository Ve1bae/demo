package com.example.video.controller;

import com.example.video.client.UserPreferenceClient;
import com.example.video.model.UserPreference;
import com.example.video.model.Video;
import com.example.video.repository.VideoRepository;
import com.example.video.service.RecommendationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(VideoRecommendationApiIntegrationTest.PreferenceTestConfig.class)
@Tag("api")
class VideoRecommendationApiIntegrationTest {
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserPreferenceClient preferenceClient;

    @TestConfiguration(proxyBeanMethods = false)
    static class PreferenceTestConfig {
        @Bean
        @Primary
        UserPreferenceClient preferenceClient() {
            return mock(UserPreferenceClient.class);
        }

        @Bean
        @Primary
        RecommendationService recommendationService(VideoRepository repository,
                                                     UserPreferenceClient preferenceClient) {
            return new com.example.video.service.RecommendationServiceImpl(repository, preferenceClient);
        }
    }

    @BeforeEach
    void setUp() {
        cleanVideos();
    }

    @AfterEach
    void tearDown() {
        cleanVideos();
    }

    @Test
    @DisplayName("API-TC-03-01 游客只获取公开视频并按热度排序")
    void guestGetsPublicVideosByScore() throws Exception {
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
    @DisplayName("API-TC-03-02 登录用户关注作者视频优先")
    void followedAuthorGetsPriority() throws Exception {
        insertVideo("popular", 11L, 1, "public", 500, 0, 0, 0, "news", BASE_TIME);
        insertVideo("followed", 10L, 1, "public", 0, 0, 0, 0, "daily", BASE_TIME.minusDays(10));
        when(preferenceClient.getPreference(42L))
                .thenReturn(new UserPreference(Set.of(10L), Map.of(), Set.of()));

        mockMvc.perform(get("/api/videos/recommend").header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title", is("followed")))
                .andExpect(jsonPath("$.data[0].authorInfo.following", is(true)));
    }

    @Test
    @DisplayName("API-TC-03-03 兴趣标签匹配视频优先")
    void interestTagGetsPriority() throws Exception {
        insertVideo("generic", 11L, 1, "public", 300, 0, 0, 0, "news", BASE_TIME);
        insertVideo("music", 12L, 1, "public", 0, 0, 0, 0, "music campus", BASE_TIME.minusDays(1));
        when(preferenceClient.getPreference(42L))
                .thenReturn(new UserPreference(Set.of(), Map.of("music", 80), Set.of()));

        mockMvc.perform(get("/api/videos/recommend").header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title", is("music")));
    }

    @Test
    @DisplayName("API-TC-03-04 本地观看历史视频降权")
    void viewedVideoGetsPenalty() throws Exception {
        long viewedId = insertVideo("viewed", 11L, 1, "public", 100, 0, 0, 0, "daily", BASE_TIME);
        insertVideo("unseen", 12L, 1, "public", 100, 0, 0, 0, "daily", BASE_TIME);
        jdbcTemplate.update("INSERT INTO view_history (user_id, video_id) VALUES (?, ?)", 42L, viewedId);

        mockMvc.perform(get("/api/videos/recommend").header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title", is("unseen")))
                .andExpect(jsonPath("$.data[1].title", is("viewed")));
    }

    @Test
    @DisplayName("API-TC-03-05 分类和关键词同时筛选")
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
    @DisplayName("API-TC-03-06 分页返回排序后的指定页")
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
    @DisplayName("API-TC-03-07 无匹配结果返回空数组")
    void returnsEmptyForNoMatch() throws Exception {
        insertVideo("existing", 11L, 1, "public", 0, 0, 0, 0, "daily", BASE_TIME);

        mockMvc.perform(get("/api/videos/recommend").param("keyword", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("API-TC-03-08 非法页码返回 400")
    void rejectsInvalidPage() throws Exception {
        mockMvc.perform(get("/api/videos/recommend").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)));
    }

    @Test
    @DisplayName("API-TC-03-09 非法用户标识返回 400")
    void rejectsInvalidUserHeader() throws Exception {
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
                title, "UC-03 API test data", "https://example.test/" + title + ".mp4",
                userId, categoryId, tags, status, playCount, likeCount, favoriteCount, commentCount,
                Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
        return jdbcTemplate.queryForObject("SELECT id FROM video WHERE title = ?", Long.class, title);
    }

    private void cleanVideos() {
        jdbcTemplate.update("DELETE FROM view_history");
        jdbcTemplate.update("DELETE FROM video");
    }
}
