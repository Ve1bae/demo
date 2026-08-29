package com.example.video.controller;

import com.example.video.model.Video;
import com.example.video.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class VideoRecommendationControllerTest {
    @Mock
    private RecommendationService recommendationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new VideoRecommendationController(recommendationService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("UNIT-TC-03-19 游客使用默认查询参数")
    void usesDefaultParameters() throws Exception {
        Video video = video(7L, "首页推荐");
        when(recommendationService.recommend(null, 1, 12, 0, null)).thenReturn(List.of(video));
        mockMvc.perform(get("/api/videos/recommend"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(7)));
        verify(recommendationService).recommend(isNull(), eq(1), eq(12), eq(0), isNull());
    }

    @Test
    @DisplayName("UNIT-TC-03-20 转发筛选和用户参数")
    void forwardsParameters() throws Exception {
        when(recommendationService.recommend(42L, 3, 7, 5, "校园")).thenReturn(List.of(video(8L, "校园音乐")));
        mockMvc.perform(get("/api/videos/recommend").param("page", "3").param("pageSize", "7")
                        .param("categoryId", "5").param("keyword", "校园").header("X-User-Id", "42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id", is(8)));
        verify(recommendationService).recommend(42L, 3, 7, 5, "校园");
    }

    @Test
    @DisplayName("UNIT-TC-03-21 空结果序列化为空数组")
    void serializesEmptyResults() throws Exception {
        when(recommendationService.recommend(null, 1, 12, 0, null)).thenReturn(List.of());
        mockMvc.perform(get("/api/videos/recommend")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("UNIT-TC-03-22 缺少用户头按游客处理")
    void treatsMissingUserAsGuest() throws Exception {
        when(recommendationService.recommend(null, 1, 12, 0, "音乐")).thenReturn(List.of());
        mockMvc.perform(get("/api/videos/recommend").param("keyword", "音乐"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        verify(recommendationService).recommend(isNull(), eq(1), eq(12), eq(0), eq("音乐"));
    }

    @Test
    @DisplayName("UNIT-TC-03-23 非数字页码返回 400")
    void rejectsInvalidPage() throws Exception {
        mockMvc.perform(get("/api/videos/recommend").param("page", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recommendationService);
    }

    @Test
    @DisplayName("UNIT-TC-03-24 非数字用户标识返回 400")
    void rejectsInvalidUserHeader() throws Exception {
        mockMvc.perform(get("/api/videos/recommend").header("X-User-Id", "guest"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recommendationService);
    }

    private Video video(Long id, String title) {
        Video video = new Video();
        video.setId(id);
        video.setTitle(title);
        video.setStatus("public");
        return video;
    }
}
