package com.example.demo;

import com.example.demo.entity.Danmaku;
import com.example.demo.mapper.DanmakuMapper;
import com.example.demo.service.impl.DanmakuServiceImpl;
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
 * UC-06 发送视频弹幕 —— 测试合集（对应顺序图 3.3.4）
 * 分三层：单元（Mockito）/ 集成 API（MockMvc+H2）/ 端到端流程。
 */
@DisplayName("UC-06 发送视频弹幕 测试合集")
public class UC06Test {

    // ============================================================
    // 单元测试：DanmakuServiceImpl（Mockito，不访问数据库）
    // ============================================================
    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("UC-06 单元测试 - DanmakuServiceImpl")
    class UnitTest {

        @Mock
        private DanmakuMapper danmakuMapper;

        @InjectMocks
        private DanmakuServiceImpl danmakuService;

        @BeforeEach
        void injectBaseMapper() {
            ReflectionTestUtils.setField(danmakuService, "baseMapper", danmakuMapper);
        }

        private Danmaku newDanmaku(String videoUrl, String content, Double time, String color) {
            Danmaku d = new Danmaku();
            d.setVideoUrl(videoUrl);
            d.setContent(content);
            d.setTime(time);
            d.setColor(color);
            d.setUserId("1");
            return d;
        }

        @Test
        @DisplayName("发送弹幕：应设置 createdAt 并插入成功")
        void saveDanmaku_应设置createdAt并插入() {
            Danmaku d = newDanmaku("http://localhost/video.mp4", "前方高能", 12.0, "#ffffff");
            when(danmakuMapper.insert(d)).thenReturn(1);

            boolean success = danmakuService.saveDanmaku(d);

            assertTrue(success);
            assertNotNull(d.getCreatedAt());
            verify(danmakuMapper, times(1)).insert(d);
        }

        @Test
        @DisplayName("发送弹幕：插入影响 0 行应返回 false（对应保存失败异常分支）")
        void saveDanmaku_插入失败_返回false() {
            Danmaku d = newDanmaku("http://localhost/video.mp4", "弹幕", 5.0, "#ff0000");
            when(danmakuMapper.insert(d)).thenReturn(0);

            boolean success = danmakuService.saveDanmaku(d);

            assertFalse(success);
        }

        @Test
        @DisplayName("按视频 URL 查询弹幕：应返回该视频全部弹幕")
        void getDanmakuByVideoUrl_应返回列表() {
            Danmaku d1 = newDanmaku("u1", "a", 1.0, "#ffffff");
            Danmaku d2 = newDanmaku("u1", "b", 2.0, "#ffffff");
            when(danmakuMapper.selectList(any())).thenReturn(List.of(d1, d2));

            List<Danmaku> result = danmakuService.getDanmakuByVideoUrl("u1");

            assertEquals(2, result.size());
            verify(danmakuMapper).selectList(any());
        }

        @Test
        @DisplayName("按用户 ID 查询弹幕：应返回该用户发送的弹幕")
        void getDanmakuByUserId_应返回列表() {
            when(danmakuMapper.selectList(any())).thenReturn(List.of(newDanmaku("u1", "x", 1.0, "#ffffff")));

            List<Danmaku> result = danmakuService.getDanmakuByUserId("1");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("删除弹幕：记录存在应返回 true")
        void deleteDanmaku_存在_返回true() {
            when(danmakuMapper.deleteById(1L)).thenReturn(1);

            boolean success = danmakuService.deleteDanmaku(1L);

            assertTrue(success);
            verify(danmakuMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除弹幕：记录不存在应返回 false")
        void deleteDanmaku_不存在_返回false() {
            when(danmakuMapper.deleteById(999L)).thenReturn(0);

            boolean success = danmakuService.deleteDanmaku(999L);

            assertFalse(success);
        }
    }

    // ============================================================
    // 集成/API 测试：DanmakuController（MockMvc + H2）
    // ============================================================
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    @DisplayName("UC-06 集成/API 测试 - DanmakuController")
    class IntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("主成功流程：向存在视频发送弹幕应成功")
        void sendDanmaku_视频存在_发送成功() throws Exception {
            mockMvc.perform(post("/api/videos/1/danmakus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "content", "前方高能",
                                    "timeSeconds", 15,
                                    "color", "#ff0000",
                                    "userId", 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("弹幕发送成功"));
        }

        @Test
        @DisplayName("异常流程：向不存在视频发送弹幕返回 404")
        void sendDanmaku_视频不存在_返回404() throws Exception {
            mockMvc.perform(post("/api/videos/999/danmakus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "弹幕", "timeSeconds", 1))))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"));
        }

        @Test
        @DisplayName("异常流程：弹幕内容为空返回 400 提示修改")
        void sendDanmaku_内容为空_返回400() throws Exception {
            mockMvc.perform(post("/api/videos/1/danmakus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("弹幕内容不能为空"));
        }

        @Test
        @DisplayName("备选流程：timeSeconds 以字符串传入应解析为时间点")
        void sendDanmaku_timeSeconds为字符串_解析成功() throws Exception {
            mockMvc.perform(post("/api/videos/1/danmakus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "content", "字符串时间",
                                    "timeSeconds", "12.5",
                                    "userId", 1))))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/api/videos/1/danmakus").param("startTime", "0").param("endTime", "60"))
                    .andExpect(jsonPath("$.data[0].time").value(12.5))
                    .andExpect(jsonPath("$.data[0].content").value("字符串时间"));
        }

        @Test
        @DisplayName("备选流程：未提供 userId 时默认 anonymous")
        void sendDanmaku_缺省userId_默认anonymous() throws Exception {
            mockMvc.perform(post("/api/videos/1/danmakus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "content", "匿名弹幕",
                                    "timeSeconds", 5))))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/api/videos/1/danmakus").param("startTime", "0").param("endTime", "60"))
                    .andExpect(jsonPath("$.data[0].userId").value("anonymous"));
        }

        @Test
        @DisplayName("备选流程：按时间区间过滤弹幕（区间外应被过滤）")
        void getDanmaku_按时间过滤() throws Exception {
            mockMvc.perform(post("/api/videos/1/danmakus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "区间内", "timeSeconds", 12.5, "userId", 1))))
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/api/videos/1/danmakus").param("startTime", "0").param("endTime", "60"))
                    .andExpect(jsonPath("$.data.length()").value(1));

            mockMvc.perform(get("/api/videos/1/danmakus").param("startTime", "20").param("endTime", "60"))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("异常流程：查询不存在视频的弹幕返回 404")
        void getDanmaku_视频不存在_返回404() throws Exception {
            mockMvc.perform(get("/api/videos/999/danmakus"))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"));
        }
    }

    // ============================================================
    // 端到端测试：完整弹幕流程（顺序图 3.3.4）
    // ============================================================
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    @DisplayName("UC-06 端到端流程")
    class E2ETest {

        @Autowired
        private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("主成功流程（视频存在分支）：发送弹幕 → 查询列表 → 屏幕展示该弹幕")
        void sendDanmaku_videoExists_happyPath() throws Exception {
            mockMvc.perform(post("/api/videos/1/danmakus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "content", "高能预警",
                                    "timeSeconds", 18.5,
                                    "color", "#00ff00",
                                    "userId", 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("弹幕发送成功"));

            mockMvc.perform(get("/api/videos/1/danmakus").param("startTime", "0").param("endTime", "60"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].content").value("高能预警"))
                    .andExpect(jsonPath("$.data[0].time").value(18.5))
                    .andExpect(jsonPath("$.data[0].color").value("#00ff00"))
                    .andExpect(jsonPath("$.data[0].videoUrl").value("http://localhost/video.mp4"));
        }

        @Test
        @DisplayName("异常流程（视频不存在分支）：发送弹幕返回错误提示")
        void sendDanmaku_videoNotExists_error() throws Exception {
            mockMvc.perform(post("/api/videos/9999/danmakus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "无视频", "timeSeconds", 1))))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("视频不存在"));
        }
    }
}
