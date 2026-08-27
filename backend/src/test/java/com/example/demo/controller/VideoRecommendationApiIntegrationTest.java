package com.example.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_UC03_API_TESTS", matches = "true")
@Tag("api")
class VideoRecommendationApiIntegrationTest {

    private static final String DATA_PREFIX = "uc03_api_";
    private static final LocalDateTime OLD_DATE = LocalDateTime.of(2025, 1, 1, 12, 0);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Long viewerId;
    private Long followedAuthorId;
    private Long otherAuthorId;

    @BeforeEach
    void setUp() {
        cleanTestData();
        viewerId = insertUser(DATA_PREFIX + "viewer", "推荐测试用户");
        followedAuthorId = insertUser(DATA_PREFIX + "followed", "已关注作者");
        otherAuthorId = insertUser(DATA_PREFIX + "other", "其他作者");
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    @Test
    @DisplayName("API-TC-03-01 游客获取推荐：只返回公开视频并按热度排序")
    void guestRecommendationReturnsPublicVideosInScoreOrder() throws Exception {
        insertVideo(DATA_PREFIX + "low", otherAuthorId, 1, "public", 80, 0, 0, 0, "daily", OLD_DATE);
        insertVideo(DATA_PREFIX + "high", followedAuthorId, 1, "public", 8000, 80, 30, 20, "tech", OLD_DATE);
        insertVideo(DATA_PREFIX + "private", otherAuthorId, 1, "private", 50000, 5000, 3000, 2000, "hidden", OLD_DATE);

        ApiResult result = get("/api/videos/recommend", null);

        assertSuccess(result);
        List<String> titles = titles(result.body());
        assertTrue(titles.indexOf(DATA_PREFIX + "high") < titles.indexOf(DATA_PREFIX + "low"));
        assertFalse(titles.contains(DATA_PREFIX + "private"));
    }

    @Test
    @DisplayName("API-TC-03-02 登录用户推荐：已关注作者的视频优先并标记关注状态")
    void followedAuthorReceivesPriorityForLoggedInUser() throws Exception {
        insertVideo(DATA_PREFIX + "popular", otherAuthorId, 1, "public", 4000, 0, 0, 0, "news", OLD_DATE);
        insertVideo(DATA_PREFIX + "followed_video", followedAuthorId, 1, "public", 0, 0, 0, 0, "daily", OLD_DATE);
        jdbcTemplate.update(
                "INSERT INTO user_follow (user_id, follow_user_id, created_at) VALUES (?, ?, NOW())",
                viewerId, followedAuthorId);

        ApiResult result = get("/api/videos/recommend", viewerId);

        assertSuccess(result);
        assertEquals(DATA_PREFIX + "followed_video", result.body().path("data").get(0).path("title").asText());
        JsonNode followedVideo = findVideo(result.body(), DATA_PREFIX + "followed_video");
        assertTrue(followedVideo.path("authorInfo").path("following").asBoolean());
    }

    @Test
    @DisplayName("API-TC-03-03 登录用户推荐：兴趣标签匹配的视频优先")
    void interestTagRaisesRecommendationPriority() throws Exception {
        insertVideo(DATA_PREFIX + "generic", otherAuthorId, 1, "public", 3200, 0, 0, 0, "news", OLD_DATE);
        insertVideo(DATA_PREFIX + "music", followedAuthorId, 1, "public", 0, 0, 0, 0, "music campus", OLD_DATE);
        jdbcTemplate.update(
                "INSERT INTO user_interest (user_id, tag, score, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())",
                viewerId, "music", 80);

        ApiResult result = get("/api/videos/recommend", viewerId);

        assertSuccess(result);
        assertEquals(DATA_PREFIX + "music", result.body().path("data").get(0).path("title").asText());
    }

    @Test
    @DisplayName("API-TC-03-04 登录用户推荐：已观看视频降低排序权重")
    void viewedVideoReceivesRecommendationPenalty() throws Exception {
        Long viewedVideoId = insertVideo(
                DATA_PREFIX + "viewed", followedAuthorId, 1, "public", 0, 0, 0, 0, "daily", OLD_DATE);
        insertVideo(DATA_PREFIX + "unseen", otherAuthorId, 1, "public", 0, 0, 0, 0, "daily", OLD_DATE);
        jdbcTemplate.update(
                "INSERT INTO view_history (user_id, video_id, view_count, progress_seconds, last_viewed_at) "
                        + "VALUES (?, ?, 1, 0, NOW())",
                viewerId, viewedVideoId);

        ApiResult result = get("/api/videos/recommend", viewerId);

        assertSuccess(result);
        List<String> titles = titles(result.body());
        assertTrue(titles.indexOf(DATA_PREFIX + "unseen") < titles.indexOf(DATA_PREFIX + "viewed"));
    }

    @Test
    @DisplayName("API-TC-03-05 分类和关键词筛选：仅返回同时匹配的视频")
    void categoryAndKeywordFilterReturnMatchingVideoOnly() throws Exception {
        insertVideo(DATA_PREFIX + "campus_music", followedAuthorId, 3, "public", 0, 0, 0, 0, "campus music", OLD_DATE);
        insertVideo(DATA_PREFIX + "campus_sport", otherAuthorId, 3, "public", 0, 0, 0, 0, "sport", OLD_DATE);
        insertVideo(DATA_PREFIX + "other_category", otherAuthorId, 4, "public", 0, 0, 0, 0, "campus music", OLD_DATE);

        ApiResult result = get("/api/videos/recommend?categoryId=3&keyword=music", null);

        assertSuccess(result);
        assertEquals(List.of(DATA_PREFIX + "campus_music"), titles(result.body()));
    }

    @Test
    @DisplayName("API-TC-03-06 推荐分页：第二页返回排序后的第二条视频")
    void paginationReturnsRequestedSlice() throws Exception {
        insertVideo(DATA_PREFIX + "rank_1", followedAuthorId, 1, "public", 8000, 0, 0, 0, "one", OLD_DATE);
        insertVideo(DATA_PREFIX + "rank_2", otherAuthorId, 1, "public", 4000, 0, 0, 0, "two", OLD_DATE);
        insertVideo(DATA_PREFIX + "rank_3", otherAuthorId, 1, "public", 80, 0, 0, 0, "three", OLD_DATE);

        ApiResult result = get("/api/videos/recommend?page=2&pageSize=1", null);

        assertSuccess(result);
        assertEquals(List.of(DATA_PREFIX + "rank_2"), titles(result.body()));
    }

    @Test
    @DisplayName("API-TC-03-07 无匹配推荐：返回成功和空数组")
    void noMatchingRecommendationReturnsEmptyArray() throws Exception {
        insertVideo(DATA_PREFIX + "existing", otherAuthorId, 1, "public", 0, 0, 0, 0, "daily", OLD_DATE);

        ApiResult result = get(
                "/api/videos/recommend?keyword=" + encode(DATA_PREFIX + "missing"), null);

        assertSuccess(result);
        assertTrue(result.body().path("data").isArray());
        assertTrue(result.body().path("data").isEmpty());
    }

    @Test
    @DisplayName("API-TC-03-08 页码类型错误：返回 HTTP 400")
    void invalidPageReturnsBadRequest() throws Exception {
        ApiResult result = get("/api/videos/recommend?page=abc", null);

        assertEquals(400, result.statusCode());
    }

    @Test
    @DisplayName("API-TC-03-09 用户标识类型错误：返回 HTTP 400")
    void invalidUserHeaderReturnsBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint("/api/videos/recommend"))
                .header("Accept", "application/json")
                .header("X-User-Id", "guest")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    private ApiResult get(String path, Long userId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(path))
                .header("Accept", "application/json")
                .GET();
        if (userId != null) {
            builder.header("X-User-Id", userId.toString());
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResult(response.statusCode(), objectMapper.readTree(response.body()));
    }

    private URI endpoint(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void assertSuccess(ApiResult result) {
        assertEquals(200, result.statusCode());
        assertEquals(200, result.body().path("code").asInt());
        assertTrue(result.body().path("data").isArray());
    }

    private List<String> titles(JsonNode body) {
        List<String> result = new ArrayList<>();
        body.path("data").forEach(video -> result.add(video.path("title").asText()));
        return result;
    }

    private JsonNode findVideo(JsonNode body, String title) {
        for (JsonNode video : body.path("data")) {
            if (title.equals(video.path("title").asText())) {
                return video;
            }
        }
        throw new AssertionError("未找到视频: " + title);
    }

    private Long insertUser(String username, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, nickname) VALUES (?, ?, ?)",
                username, "test-password", nickname);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
    }

    private Long insertVideo(
            String title,
            Long userId,
            int categoryId,
            String status,
            int playCount,
            int likeCount,
            int favoriteCount,
            int commentCount,
            String tags,
            LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO video (title, description, play_url, user_id, category_id, tags, status, "
                        + "play_count, like_count, favorite_count, comment_count, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                title,
                "UC-03 API test data",
                "https://example.test/" + title + ".mp4",
                userId,
                categoryId,
                tags,
                status,
                playCount,
                likeCount,
                favoriteCount,
                commentCount,
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt));
        return jdbcTemplate.queryForObject("SELECT id FROM video WHERE title = ?", Long.class, title);
    }

    private void cleanTestData() {
        jdbcTemplate.update(
                "DELETE FROM user_follow WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?) "
                        + "OR follow_user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)",
                DATA_PREFIX + "%", DATA_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM user_interest WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)",
                DATA_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM view_history WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?) "
                        + "OR video_id IN (SELECT id FROM video WHERE title LIKE ?)",
                DATA_PREFIX + "%", DATA_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM user_video WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?) "
                        + "OR video_id IN (SELECT id FROM video WHERE title LIKE ?)",
                DATA_PREFIX + "%", DATA_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM video WHERE title LIKE ?", DATA_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", DATA_PREFIX + "%");
    }

    private record ApiResult(int statusCode, JsonNode body) {
    }
}
