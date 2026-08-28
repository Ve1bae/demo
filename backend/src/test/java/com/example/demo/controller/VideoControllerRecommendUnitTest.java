package com.example.demo.controller;

import com.example.demo.entity.Video;
import com.example.demo.service.MinioService;
import com.example.demo.service.VideoService;
import com.example.demo.service.VideoTranscodeService;
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

/**
 * UC-03 推荐视频控制器单元测试。
 *
 * <p>使用 standalone MockMvc，仅替换 VideoService，因而不会连接 MySQL、MinIO
 * 或其它外部依赖；测试关注控制器的参数绑定、请求转发和响应结构。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class VideoControllerRecommendUnitTest {

    @Mock
    private VideoService videoService;

    @Mock
    private MinioService minioService;

    @Mock
    private VideoTranscodeService videoTranscodeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VideoController controller = new VideoController(videoService, minioService, videoTranscodeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("UNIT-TC-03-19 游客请求使用接口默认参数")
    void usesDocumentedDefaultQueryParametersForGuest() throws Exception {
        Video video = video(7L, "首页推荐");
        when(videoService.getRecommendedFeed(null, 1, 12, 0, null)).thenReturn(List.of(video));

        mockMvc.perform(get("/api/videos/recommend"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.message", is("success")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(7)))
                .andExpect(jsonPath("$.data[0].title", is("首页推荐")));

        verify(videoService).getRecommendedFeed(isNull(), eq(1), eq(12), eq(0), isNull());
    }

    @Test
    @DisplayName("UNIT-TC-03-20 Controller正确转发分页筛选和用户参数")
    void forwardsPagingFiltersKeywordAndLoggedInUserHeader() throws Exception {
        Video video = video(8L, "校园音乐会");
        when(videoService.getRecommendedFeed(42L, 3, 7, 5, "校园"))
                .thenReturn(List.of(video));

        mockMvc.perform(get("/api/videos/recommend")
                        .param("page", "3")
                        .param("pageSize", "7")
                        .param("categoryId", "5")
                        .param("keyword", "校园")
                        .header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data[0].id", is(8)));

        verify(videoService).getRecommendedFeed(eq(42L), eq(3), eq(7), eq(5), eq("校园"));
    }

    @Test
    @DisplayName("UNIT-TC-03-21 Service空结果被序列化为空数组")
    void returnsAnEmptyArrayWhenRecommendationHasNoResults() throws Exception {
        when(videoService.getRecommendedFeed(null, 1, 12, 0, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/videos/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(0)));

        verify(videoService).getRecommendedFeed(isNull(), eq(1), eq(12), eq(0), isNull());
    }

    @Test
    @DisplayName("UNIT-TC-03-22 缺少用户请求头时按游客处理")
    void acceptsExplicitGuestHeaderOmissionAlongsideKeyword() throws Exception {
        when(videoService.getRecommendedFeed(null, 1, 12, 0, "音乐")).thenReturn(List.of());

        mockMvc.perform(get("/api/videos/recommend").param("keyword", "音乐"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(0)));

        verify(videoService).getRecommendedFeed(isNull(), eq(1), eq(12), eq(0), eq("音乐"));
    }

    @Test
    @DisplayName("UNIT-TC-03-23 非数字页码返回HTTP 400且不调用Service")
    void rejectsNonNumericPagingParameterBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/videos/recommend").param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(videoService);
    }

    @Test
    @DisplayName("UNIT-TC-03-24 非数字用户标识返回HTTP 400且不调用Service")
    void rejectsNonNumericUserHeaderBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/videos/recommend").header("X-User-Id", "guest"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(videoService);
    }

    private Video video(Long id, String title) {
        Video video = new Video();
        video.setId(id);
        video.setTitle(title);
        video.setStatus("public");
        video.setPlayUrl("https://example.test/videos/" + id + ".mp4");
        return video;
    }
}
