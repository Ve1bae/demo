package com.example.demo;

import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentLike;
import com.example.demo.entity.User;
import com.example.demo.mapper.CommentLikeMapper;
import com.example.demo.mapper.CommentMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.impl.CommentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-05 发送评论 —— 测试合集（对应顺序图 3.3.3）
 * 分三层：单元（Mockito）/ 集成 API（MockMvc+H2）/ 端到端流程。
 */
@DisplayName("UC-05 发送评论 测试合集")
public class UC05Test {

    // ============================================================
    // 单元测试：CommentServiceImpl（Mockito，不访问数据库）
    // ============================================================
    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("UC-05 单元测试 - CommentServiceImpl")
    class UnitTest {

        @Mock
        private CommentMapper commentMapper;

        @Mock
        private CommentLikeMapper commentLikeMapper;

        @Mock
        private UserMapper userMapper;

        @InjectMocks
        private CommentServiceImpl commentService;

        @BeforeEach
        void injectBaseMapper() {
            ReflectionTestUtils.setField(commentService, "baseMapper", commentMapper);
        }

        @Test
        @DisplayName("保存评论：likeCount 为 null 时应初始化为 0 并插入成功")
        void saveComment_likeCount为null_应初始化为0() {
            Comment comment = new Comment();
            comment.setVideoId(1L);
            comment.setUserId(1L);
            comment.setContent("测试评论");
            when(commentMapper.insert(comment)).thenReturn(1);

            boolean success = commentService.saveComment(comment);

            assertTrue(success);
            assertEquals(0, comment.getLikeCount());
            verify(commentMapper, times(1)).insert(comment);
        }

        @Test
        @DisplayName("保存评论：已有 likeCount 应保持不变")
        void saveComment_已有likeCount_保持不变() {
            Comment comment = new Comment();
            comment.setLikeCount(5);
            comment.setContent("赞过的评论");
            when(commentMapper.insert(comment)).thenReturn(1);

            boolean success = commentService.saveComment(comment);

            assertTrue(success);
            assertEquals(5, comment.getLikeCount());
        }

        @Test
        @DisplayName("分页查询：应回填 commentId 和用户信息，并返回 total/page")
        void getCommentsWithPagination_应回填用户信息() {
            Comment c = new Comment();
            c.setId(10L);
            c.setVideoId(1L);
            c.setUserId(1L);
            c.setContent("一条评论");
            User user = new User();
            user.setId(1L);
            user.setNickname("测试用户");

            when(commentMapper.selectList(any())).thenReturn(List.of(c));
            when(userMapper.selectById(1L)).thenReturn(user);

            Map<String, Object> result = commentService.getCommentsByVideoIdWithPagination(1L, 1, 20);

            assertEquals(1, result.get("total"));
            assertEquals(1, result.get("page"));
            assertEquals(20, result.get("pageSize"));
            @SuppressWarnings("unchecked")
            List<Comment> list = (List<Comment>) result.get("list");
            assertEquals(1, list.size());
            assertEquals(10L, list.get(0).getCommentId());
            assertNotNull(list.get(0).getUser());
            assertEquals("测试用户", list.get(0).getUser().getNickname());
        }

        @Test
        @DisplayName("分页查询：用户不存在时 user 为 null 不应抛异常")
        void getCommentsWithPagination_用户不存在_user为null() {
            Comment c = new Comment();
            c.setId(11L);
            c.setUserId(99L);
            c.setContent("无用户评论");

            when(commentMapper.selectList(any())).thenReturn(List.of(c));
            when(userMapper.selectById(99L)).thenReturn(null);

            Map<String, Object> result = commentService.getCommentsByVideoIdWithPagination(1L, 1, 20);

            @SuppressWarnings("unchecked")
            List<Comment> list = (List<Comment>) result.get("list");
            assertNull(list.get(0).getUser());
        }

        @Test
        @DisplayName("点赞评论：评论存在应 likeCount+1 并更新成功")
        void likeComment_存在_点赞数加一() {
            Comment c = new Comment();
            c.setId(20L);
            c.setLikeCount(3);
            when(commentMapper.selectById(20L)).thenReturn(c);
            when(commentLikeMapper.insert(any(CommentLike.class))).thenReturn(1);
            when(commentMapper.updateById(c)).thenReturn(1);

            boolean success = commentService.likeComment(20L, 1L);

            assertTrue(success);
            assertEquals(4, c.getLikeCount());
            verify(commentLikeMapper).insert(any(CommentLike.class));
            verify(commentMapper).updateById(c);
        }

        @Test
        @DisplayName("点赞评论：评论不存在应返回 false 且不执行更新")
        void likeComment_不存在_返回false() {
            when(commentMapper.selectById(999L)).thenReturn(null);

            boolean success = commentService.likeComment(999L, 1L);

            assertFalse(success);
            verify(commentLikeMapper, never()).insert(any(CommentLike.class));
            verify(commentMapper, never()).updateById(any(Comment.class));
        }

        @Test
        @DisplayName("删除评论：应通过 removeById 调用 deleteById")
        void deleteComment_应调用删除() {
            when(commentMapper.deleteById(30L)).thenReturn(1);

            boolean success = commentService.deleteComment(30L);

            assertTrue(success);
            verify(commentMapper).deleteById(30L);
        }

        @Test
        @DisplayName("删除评论：评论不存在（删除影响 0 行）应返回 false")
        void deleteComment_不存在_返回false() {
            when(commentMapper.deleteById(999L)).thenReturn(0);

            boolean success = commentService.deleteComment(999L);

            assertFalse(success);
        }
    }

    // ============================================================
    // 集成/API 测试：CommentController（MockMvc + H2）
    // ============================================================
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    @TestPropertySource(properties = {
            "minio.endpoint=http://127.0.0.1:9000",
            "minio.public-base-url=http://127.0.0.1:8082/video",
            "minio.access-key=minioadmin",
            "minio.secret-key=minioadmin",
            "minio.bucket=hangyin-video",
            "live.srs.rtmp-base-url=rtmp://127.0.0.1/live",
            "live.srs.http-base-url=http://127.0.0.1:8081",
            "video.transcode.enabled=false"
    })
    @Sql(statements = {
            "INSERT INTO sys_user (id, username, password, nickname, avatar_url) VALUES (1, 'tester', 'pwd', '测试用户', 'http://localhost/avatar.jpg') ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), avatar_url=VALUES(avatar_url)",
            "INSERT INTO video (id, title, description, cover_url, play_url, user_id, category_id, duration, status, play_count, like_count, favorite_count, comment_count) VALUES (1, '测试视频', '用于评论测试', 'http://localhost/cover.jpg', 'http://localhost/video.mp4', 1, 0, 60, 'public', 0, 0, 0, 0) ON DUPLICATE KEY UPDATE play_count=VALUES(play_count), like_count=VALUES(like_count), comment_count=VALUES(comment_count)"
    })
    @DisplayName("UC-05 集成/API 测试 - CommentController")
    class IntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("主成功流程：向存在视频发送评论应成功")
        void postComment_视频存在_发布成功() throws Exception {
            mockMvc.perform(post("/api/videos/1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "评论A", "userId", 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("评论发布成功"));
        }

        @Test
        @DisplayName("异常流程：向不存在视频发送评论返回 404")
        void postComment_视频不存在_返回404() throws Exception {
            mockMvc.perform(post("/api/videos/999/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "评论B", "userId", 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"));
        }

        @Test
        @DisplayName("异常流程：评论内容为空返回 400 提示修改")
        void postComment_内容为空_返回400() throws Exception {
            mockMvc.perform(post("/api/videos/1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("评论内容不能为空"));
        }

        @Test
        @DisplayName("异常流程：评论内容为空白字符串返回 400")
        void postComment_内容为空白_返回400() throws Exception {
            mockMvc.perform(post("/api/videos/1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "   ", "userId", 1))))
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("评论内容不能为空"));
        }

        @Test
        @DisplayName("主成功流程：发送评论后重新加载列表，列表中包含该评论")
        void postComment_thenReloadList_列表包含新评论() throws Exception {
            mockMvc.perform(post("/api/videos/1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "刷新验证评论", "userId", 1))))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/api/videos/1/comments").param("page", "1").param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].content").value("刷新验证评论"))
                    .andExpect(jsonPath("$.data.list[0].commentId").exists());
        }

        @Test
        @DisplayName("备选流程：查询不存在视频的评论返回 404")
        void getComments_视频不存在_返回404() throws Exception {
            mockMvc.perform(get("/api/videos/999/comments"))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"));
        }
    }

    // ============================================================
    // 端到端测试：完整评论流程（顺序图 3.3.3）
    // ============================================================
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    @TestPropertySource(properties = {
            "minio.endpoint=http://127.0.0.1:9000",
            "minio.public-base-url=http://127.0.0.1:8082/video",
            "minio.access-key=minioadmin",
            "minio.secret-key=minioadmin",
            "minio.bucket=hangyin-video",
            "live.srs.rtmp-base-url=rtmp://127.0.0.1/live",
            "live.srs.http-base-url=http://127.0.0.1:8081",
            "video.transcode.enabled=false"
    })
    @Sql(statements = {
            "INSERT INTO sys_user (id, username, password, nickname, avatar_url) VALUES (1, 'tester', 'pwd', '测试用户', 'http://localhost/avatar.jpg') ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), avatar_url=VALUES(avatar_url)",
            "INSERT INTO video (id, title, description, cover_url, play_url, user_id, category_id, duration, status, play_count, like_count, favorite_count, comment_count) VALUES (1, '测试视频', '用于评论测试', 'http://localhost/cover.jpg', 'http://localhost/video.mp4', 1, 0, 60, 'public', 0, 0, 0, 0) ON DUPLICATE KEY UPDATE play_count=VALUES(play_count), like_count=VALUES(like_count), comment_count=VALUES(comment_count)"
    })
    @DisplayName("UC-05 端到端流程")
    class E2ETest {

        @Autowired
        private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("主成功流程（视频存在分支）：提交评论 → 重新加载列表 → 新评论出现")
        void postComment_videoExists_happyPath() throws Exception {
            mockMvc.perform(post("/api/videos/1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "端到端评论", "userId", 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("评论发布成功"));

            mockMvc.perform(get("/api/videos/1/comments").param("page", "1").param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].content").value("端到端评论"))
                    .andExpect(jsonPath("$.data.list[0].commentId").exists())
                    .andExpect(jsonPath("$.data.list[0].userId").value(1));
        }

        @Test
        @DisplayName("异常流程（视频不存在分支）：提交评论返回错误提示")
        void postComment_videoNotExists_error() throws Exception {
            mockMvc.perform(post("/api/videos/9999/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "无视频", "userId", 1))))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"));
        }
    }
}
