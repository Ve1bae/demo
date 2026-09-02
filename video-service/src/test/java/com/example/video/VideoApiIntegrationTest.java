package com.example.video;

import com.example.video.controller.GlobalExceptionHandler;
import com.example.video.controller.VideoController;
import com.example.video.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class VideoApiIntegrationTest {
    private MockMvc mvc(VideoService videos) {
        return standaloneSetup(new VideoController(videos)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void nonOwnerDeleteReturns403() throws Exception {
        VideoService videos = mock(VideoService.class);
        when(videos.softDelete(1L, 10L)).thenReturn(false);
        mvc(videos).perform(delete("/api/videos/1").header("X-User-Id", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(403));
    }

    @Test void recommendReturnsArray() throws Exception {
        VideoService videos = mock(VideoService.class);
        when(videos.recommend(1, 12, null, null, 10L)).thenReturn(List.of());
        mvc(videos).perform(get("/api/videos/recommend").header("X-User-Id", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test void missingVideoReturns404() throws Exception {
        VideoService videos = mock(VideoService.class);
        when(videos.getByIdWithState(anyLong(), org.mockito.ArgumentMatchers.isNull())).thenThrow(new IllegalArgumentException("视频不存在"));
        mvc(videos).perform(get("/api/videos/99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
    }
}
