package com.example.video;

import com.example.video.common.PageResult;
import com.example.video.controller.*;
import com.example.video.service.*;
import com.example.video.vo.CommentVO;
import com.example.video.vo.DanmakuVO;
import com.example.video.vo.VideoVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** API-level coverage for every public mapping in video-service controllers. */
class ControllerApiCoverageTest {
    private static final GlobalExceptionHandler ADVICE = new GlobalExceptionHandler();

    @Test void videoReadAndStateEndpoints() throws Exception {
        VideoService service = mock(VideoService.class);
        when(service.getByUserId(anyLong(), any())).thenReturn(List.of());
        when(service.getFavoritesByUserId(anyLong(), any())).thenReturn(List.of());
        when(service.history(anyLong())).thenReturn(List.of());
        when(service.status(anyLong(), anyLong())).thenReturn(Map.of("liked", false, "favorite", false));
        MockMvc mvc = standaloneSetup(new VideoController(service)).setControllerAdvice(ADVICE).build();

        mvc.perform(get("/api/videos/user/7/uploads").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/videos/user/7/favorites").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/videos/user/7/history"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/videos/1/status").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(false));
    }

    @Test void videoMutationEndpoints() throws Exception {
        VideoService service = mock(VideoService.class);
        when(service.upload(any(org.springframework.web.multipart.MultipartFile.class), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new VideoVO());
        when(service.setVisibility(1L, 8L, true)).thenReturn(true);
        when(service.play(1L, 8L)).thenReturn(Map.of("videoId", 1L, "playCount", 1));
        when(service.toggleLike(8L, 1L)).thenReturn(Map.of("liked", true, "likeCount", 1));
        when(service.toggleFavorite(8L, 1L)).thenReturn(Map.of("favorite", true));
        MockMvc mvc = standaloneSetup(new VideoController(service)).setControllerAdvice(ADVICE).build();
        MockMultipartFile file = new MockMultipartFile("file", "v.mp4", "video/mp4", new byte[]{1});

        mvc.perform(multipart("/api/videos/upload").file(file).param("title", "demo")
                        .param("userId", "8").param("duration", "12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/videos/1/visibility").header("X-User-Id", "8")
                        .contentType(APPLICATION_JSON).content("{\"visible\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/videos/1/play").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.videoId").value(1));
        mvc.perform(post("/api/videos/1/likes").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(true));
        mvc.perform(delete("/api/videos/1/likes").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/videos/1/favorites").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.favorite").value(true));
        mvc.perform(delete("/api/videos/1/favorites").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test void commentEndpointsAndAliases() throws Exception {
        CommentService service = mock(CommentService.class);
        when(service.list(eq(1L), anyInt(), anyInt(), any())).thenReturn(new PageResult<>(List.of(), 0, 1, 20));
        when(service.add(eq(1L), eq(8L), anyString(), anyString(), isNull())).thenReturn(new CommentVO());
        when(service.like(2L, 8L)).thenReturn(Map.of("commentId", 2L, "liked", true, "likeCount", 1));
        when(service.unlike(2L, 8L)).thenReturn(Map.of("commentId", 2L, "liked", false, "likeCount", 0));
        MockMvc mvc = standaloneSetup(new CommentController(service)).setControllerAdvice(ADVICE).build();

        mvc.perform(get("/api/videos/1/comments").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list").isArray());
        mvc.perform(post("/api/videos/1/comments").header("X-User-Id", "8").header("X-Username", "tester")
                        .contentType(APPLICATION_JSON).content("{\"content\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/comments/2/likes").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(true));
        mvc.perform(delete("/api/videos/1/comments/2/like").header("X-User-Id", "8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(false));
    }

    @Test void danmakuEndpoints() throws Exception {
        DanmakuService service = mock(DanmakuService.class);
        when(service.list(1L, 100)).thenReturn(List.of());
        when(service.add(eq(1L), eq(8L), anyString(), anyString(), anyString(), anyInt())).thenReturn(new DanmakuVO());
        MockMvc mvc = standaloneSetup(new DanmakuController(service)).setControllerAdvice(ADVICE).build();
        mvc.perform(get("/api/videos/1/danmakus")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(post("/api/videos/1/danmakus").header("X-User-Id", "8").header("X-Username", "tester")
                        .contentType(APPLICATION_JSON).content("{\"content\":\"hi\",\"color\":\"#fff\",\"timeSeconds\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test void minioEndpoints() throws Exception {
        MinioService service = mock(MinioService.class);
        when(service.testConnection()).thenReturn(Map.of("connected", true));
        when(service.publicUrl(anyString())).thenReturn("http://minio/uploaded");
        when(service.publicUrl("x.mp4")).thenReturn("http://minio/x.mp4");
        doNothing().when(service).upload(any(Path.class), anyString(), anyString());
        MockMvc mvc = standaloneSetup(new MinioController(service)).setControllerAdvice(ADVICE).build();
        mvc.perform(get("/api/minio/test")).andExpect(status().isOk()).andExpect(jsonPath("$.data.connected").value(true));
        mvc.perform(get("/api/minio/url").param("objectName", "x.mp4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("http://minio/x.mp4"));
        mvc.perform(delete("/api/minio/delete").param("objectName", "x.mp4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1});
        mvc.perform(multipart("/api/minio/upload").file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }
}
