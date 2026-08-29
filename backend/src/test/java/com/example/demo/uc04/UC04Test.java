package com.example.demo.uc04;

import com.example.demo.entity.UserVideo;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserVideoMapper;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.impl.VideoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-04 播放视频 —— 测试合集（对应顺序图 3.3.2）
 * 分三层：单元（Mockito）/ 集成 API（MockMvc+H2）/ 端到端流程。
 */
@DisplayName("UC-04 播放视频 测试合集")
public class UC04Test {

    // ============================================================
    // 单元测试：VideoServiceImpl（Mockito，不访问数据库）
    // ============================================================
    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("UC-04 单元测试 - VideoServiceImpl")
    class UnitTest {

        @Mock
        private VideoMapper videoMapper;

        @Mock
        private UserVideoMapper userVideoMapper;

        @InjectMocks
        private VideoServiceImpl videoService;

        @BeforeEach
        void injectBaseMapper() {
            ReflectionTestUtils.setField(videoService, "baseMapper", videoMapper);
        }

        private Video newVideo(Long id, String playUrl, Integer playCount) {
            Video v = new Video();
            v.setId(id);
            v.setPlayUrl(playUrl);
            v.setPlayCount(playCount);
            return v;
        }

        @Test
        @DisplayName("查询视频详情：视频存在，应回填 videoId/sources/defaultQuality/views")
        void getVideoById_存在_应回填兼容字段() {
            Video v = newVideo(1L, "http://localhost/video.mp4", 5);
            when(videoMapper.selectById(1L)).thenReturn(v);

            Video result = videoService.getVideoById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getVideoId());
            assertEquals("5", result.getViews());
            assertNotNull(result.getSources());
            assertEquals(1, result.getSources().size());
            assertEquals("http://localhost/video.mp4", result.getSources().get("720P"));
            assertEquals("720P", result.getDefaultQuality());
        }

        @Test
        @DisplayName("查询视频详情：视频不存在，应返回 null（对应异常流程：资源不可用）")
        void getVideoById_不存在_应返回null() {
            when(videoMapper.selectById(999L)).thenReturn(null);

            Video result = videoService.getVideoById(999L);

            assertNull(result);
        }

        @Test
        @DisplayName("播放量格式化：playCount>=10000 应显示为“万”")
        void getVideoById_播放量过万_应格式化为万() {
            Video v = newVideo(2L, "http://localhost/v2.mp4", 12345);
            when(videoMapper.selectById(2L)).thenReturn(v);

            Video result = videoService.getVideoById(2L);

            assertEquals("1.2万", result.getViews());
        }

        @Test
        @DisplayName("播放量为 null 时不应抛空指针，views 保持为 null")
        void getVideoById_播放量为null_不报错() {
            Video v = newVideo(3L, "http://localhost/v3.mp4", null);
            when(videoMapper.selectById(3L)).thenReturn(v);

            Video result = videoService.getVideoById(3L);

            assertNotNull(result);
            assertNull(result.getViews());
        }

        @Test
        @DisplayName("无播放地址时不应设置 sources")
        void getVideoById_无播放地址_不设置sources() {
            Video v = newVideo(4L, null, 10);
            when(videoMapper.selectById(4L)).thenReturn(v);

            Video result = videoService.getVideoById(4L);

            assertNotNull(result);
            assertTrue(result.getSources().isEmpty());
        }

        @Test
        @DisplayName("通知播放开始：应调用一次播放量自增并返回最新播放量")
        void incrementPlayCount_应递增播放量() {
            doNothing().when(userVideoMapper).incrementPlayCount(1L);
            when(videoMapper.selectById(1L)).thenReturn(newVideo(1L, "u", 1));

            Map<String, Object> result = videoService.incrementPlayCount(1L);

            verify(userVideoMapper, times(1)).incrementPlayCount(1L);
            assertEquals(1L, result.get("videoId"));
            assertEquals(1, result.get("playCount"));
        }

        @Test
        @DisplayName("切换点赞：首次点赞应把 liked 置为 true 并 +1")
        void toggleLike_首次点赞_状态变true并加一() {
            UserVideo existing = new UserVideo();
            existing.setId(100L);
            existing.setUserId(1L);
            existing.setVideoId(1L);
            existing.setLiked(false);
            when(userVideoMapper.findByUserIdAndVideoId(1L, 1L)).thenReturn(existing);
            when(userVideoMapper.updateById(existing)).thenReturn(1);
            doNothing().when(userVideoMapper).updateVideoLikeCount(1L, 1);
            Video liked = newVideo(1L, "u", 5);
            liked.setLikeCount(6);
            when(videoMapper.selectById(1L)).thenReturn(liked);

            Map<String, Object> result = videoService.toggleLike(1L, 1L);

            assertTrue((Boolean) result.get("liked"));
            assertEquals(6, result.get("likeCount"));
            verify(userVideoMapper).updateVideoLikeCount(1L, 1);
        }

        @Test
        @DisplayName("获取用户对视频状态：无关系记录应返回 liked/favorited=false")
        void getUserVideoStatus_无记录_默认false() {
            when(userVideoMapper.findByUserIdAndVideoId(9L, 1L)).thenReturn(null);

            Map<String, Object> result = videoService.getUserVideoStatus(9L, 1L);

            assertEquals(Boolean.FALSE, result.get("liked"));
            assertEquals(Boolean.FALSE, result.get("favorited"));
            assertEquals(1L, result.get("videoId"));
        }
    }

    // ============================================================
    // 集成/API 测试：VideoController（MockMvc + H2）
    // ============================================================
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    @TestPropertySource(properties = {
            "minio.endpoint=http://127.0.0.1:9000",
            "minio.public-base-url=http://127.0.0.1:8082/video",
            "minio.access-key=${MINIO_ACCESS_KEY}",
            "minio.secret-key=${MINIO_SECRET_KEY}",
            "minio.bucket=hangyin-video",
            "live.srs.rtmp-base-url=rtmp://127.0.0.1/live",
            "live.srs.http-base-url=http://127.0.0.1:8081",
            "video.transcode.enabled=false"
    })
    @Sql(statements = {
            "INSERT INTO sys_user (id, username, password, nickname, avatar_url) VALUES (1, 'tester', 'pwd', '测试用户', 'http://localhost/avatar.jpg') ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), avatar_url=VALUES(avatar_url)",
            "INSERT INTO video (id, title, description, cover_url, play_url, user_id, category_id, duration, status, play_count, like_count, favorite_count, comment_count) VALUES (1, '测试视频', '用于播放测试', 'http://localhost/cover.jpg', 'http://localhost/video.mp4', 1, 0, 60, 'public', 0, 0, 0, 0) ON DUPLICATE KEY UPDATE play_count=VALUES(play_count), like_count=VALUES(like_count)"
    })
    @DisplayName("UC-04 集成/API 测试 - VideoController")
    class IntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("主成功流程：GET /api/videos/1 返回视频详情、播放地址与默认清晰度")
        void getVideoDetail_存在_返回详情() throws Exception {
            mockMvc.perform(get("/api/videos/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.playUrl").value("http://localhost/video.mp4"))
                    .andExpect(jsonPath("$.data.defaultQuality").value("720P"))
                    .andExpect(jsonPath("$.data.videoId").value(1));
        }

        @Test
        @DisplayName("异常流程：GET /api/videos/999 视频不存在返回 404 提示")
        void getVideoDetail_不存在_返回404() throws Exception {
            mockMvc.perform(get("/api/videos/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"))
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("主成功流程：POST /api/videos/1/play 通知播放开始，播放量自增")
        void notifyPlay_存在_播放量自增() throws Exception {
            mockMvc.perform(post("/api/videos/1/play"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.videoId").value(1))
                    .andExpect(jsonPath("$.data.playCount").value(1));
        }

        @Test
        @DisplayName("异常流程：POST /api/videos/999/play 视频不存在返回 404")
        void notifyPlay_不存在_返回404() throws Exception {
            mockMvc.perform(post("/api/videos/999/play"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"));
        }

        @Test
        @DisplayName("备选流程：连续两次通知播放，播放量累计为 2")
        void notifyPlay_连续两次_累计为2() throws Exception {
            mockMvc.perform(post("/api/videos/1/play"))
                    .andExpect(jsonPath("$.data.playCount").value(1));
            mockMvc.perform(post("/api/videos/1/play"))
                    .andExpect(jsonPath("$.data.playCount").value(2));
        }
    }

    // ============================================================
    // 端到端测试：完整播放流程（顺序图 3.3.2）
    // ============================================================
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    @TestPropertySource(properties = {
            "minio.endpoint=http://127.0.0.1:9000",
            "minio.public-base-url=http://127.0.0.1:8082/video",
            "minio.access-key=${MINIO_ACCESS_KEY}",
            "minio.secret-key=${MINIO_SECRET_KEY}",
            "minio.bucket=hangyin-video",
            "live.srs.rtmp-base-url=rtmp://127.0.0.1/live",
            "live.srs.http-base-url=http://127.0.0.1:8081",
            "video.transcode.enabled=false"
    })
    @Sql(statements = {
            "INSERT INTO sys_user (id, username, password, nickname, avatar_url) VALUES (1, 'tester', 'pwd', '测试用户', 'http://localhost/avatar.jpg') ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), avatar_url=VALUES(avatar_url)",
            "INSERT INTO video (id, title, description, cover_url, play_url, user_id, category_id, duration, status, play_count, like_count, favorite_count, comment_count) VALUES (1, '测试视频', '用于播放测试', 'http://localhost/cover.jpg', 'http://localhost/video.mp4', 1, 0, 60, 'public', 0, 0, 0, 0) ON DUPLICATE KEY UPDATE play_count=VALUES(play_count), like_count=VALUES(like_count)"
    })
    @DisplayName("UC-04 端到端流程")
    class E2ETest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("主成功流程：进入详情页 → 加载播放地址 → 通知播放开始 → 播放量自增为 1")
        void playVideo_happyPath() throws Exception {
            mockMvc.perform(get("/api/videos/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.playUrl").value("http://localhost/video.mp4"))
                    .andExpect(jsonPath("$.data.defaultQuality").value("720P"));

            mockMvc.perform(post("/api/videos/1/play"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.videoId").value(1))
                    .andExpect(jsonPath("$.data.playCount").value(1));
        }

        @Test
        @DisplayName("异常流程：访问不存在的视频，详情与播放通知均返回资源不可用")
        void playVideo_videoNotFound() throws Exception {
            mockMvc.perform(get("/api/videos/9999"))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"))
                    .andExpect(jsonPath("$.data").isEmpty());

            mockMvc.perform(post("/api/videos/9999/play"))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"));
        }
    }
}
