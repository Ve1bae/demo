package com.example.demo.controller;

import com.example.demo.entity.Video;
import com.example.demo.service.MinioService;
import com.example.demo.service.VideoService;
import com.example.demo.service.VideoTranscodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 测试：VideoController（UC-01 上传视频 / UC-02 管理个人视频）
 *
 * 说明：
 * 1. 使用 @WebMvcTest 只加载 Web 层（不启动 MySQL/MinIO/转码等外部服务），
 *    VideoService / MinioService / VideoTranscodeService 用 @MockitoBean 模拟；
 * 2. 覆盖老师要求的三种流程：
 *    - 主成功流程：正常上传（TC-01-1）、查看本人视频列表（TC-02-1）、删除本人视频（TC-02-2）；
 *    - 备选流程：删除时用请求体传 userId、把视频设为仅自己可见；
 *    - 异常流程：标题为空、文件为空、存储服务抛异常、转码服务抛异常（TC-01-2）、
 *      缺少用户身份、删除他人视频被拒绝（TC-02-3）、视频不存在（404）；
 *    - 备选分支：转码未产出清晰度文件时 720P 回退为原始播放地址；
 * 3. 每个测试都用 jsonPath 对返回结果做断言，并校验与下游模块（MinIO/转码服务）的调用。
 */
@WebMvcTest(VideoController.class)
// 关键：只加载 VideoController，跳过主类 DemoApplication，
// 否则 @MapperScan("com.example.demo.mapper") 会在没有数据库的切片测试里尝试创建 Mapper Bean，导致上下文加载失败
@ContextConfiguration(classes = VideoController.class)
class VideoControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VideoService videoService;

    @MockitoBean
    private MinioService minioService;

    @MockitoBean
    private VideoTranscodeService videoTranscodeService;

    // ==================== UC-01 上传视频 ====================

    @Test
    @DisplayName("TC-01-1 正常上传视频：主成功流程，应返回上传成功并保存视频")
    void uploadVideo_whenValidInput_shouldSucceed() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", "fake-video-content".getBytes());

        when(videoTranscodeService.transcodeAndUpload(any(Path.class), anyString()))
                .thenReturn(Map.of(
                        "480P", "videos/qualities/xx-480p.mp4",
                        "720P", "videos/qualities/xx-720p.mp4",
                        "1080P", "videos/qualities/xx-1080p.mp4"));
        when(videoService.save(any(Video.class))).thenReturn(true);

        Video savedVideo = new Video();
        savedVideo.setId(10L);
        savedVideo.setTitle("测试视频标题");
        savedVideo.setStatus("public");
        savedVideo.setDefaultQuality("720P");
        savedVideo.setUrl720p("http://localhost:8082/video/videos/qualities/xx-720p.mp4");
        when(videoService.getVideoById(any())).thenReturn(savedVideo);

        mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .param("title", "测试视频标题")
                        .param("userId", "100")
                        .param("tags", "校园,航音"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("上传成功"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.title").value("测试视频标题"))
                .andExpect(jsonPath("$.data.status").value("public"))
                .andExpect(jsonPath("$.data.defaultQuality").value("720P"))
                .andExpect(jsonPath("$.data.url720p").exists());

        // 校验控制器与下游模块的协作：先上传 MinIO，再调用转码服务
        verify(minioService).uploadLocalFile(any(Path.class), startsWith("videos/"), eq("video/mp4"));
        verify(videoTranscodeService).transcodeAndUpload(any(Path.class), eq("videos/qualities"));
        verify(videoService).save(any(Video.class));
    }

    @Test
    @DisplayName("TC-01-2 标题为空（异常流）：应返回 400 视频标题不能为空")
    void uploadVideo_whenTitleBlank_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", "fake-video-content".getBytes());

        mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .param("title", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("视频标题不能为空"));

        verify(videoService, never()).save(any(Video.class));
        verify(minioService, never()).uploadLocalFile(any(Path.class), anyString(), anyString());
    }

    @Test
    @DisplayName("TC-01-2 未选择文件（异常流）：应返回 400 请选择要上传的视频文件")
    void uploadVideo_whenFileEmpty_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[0]);

        mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .param("title", "只有标题没有文件"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择要上传的视频文件"));

        verify(videoService, never()).save(any(Video.class));
    }

    @Test
    @DisplayName("TC-01-2 存储服务异常（异常流）：MinIO 抛异常应返回 500 视频上传失败")
    void uploadVideo_whenMinioThrows_shouldReturn500() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", "fake-video-content".getBytes());

        doThrow(new RuntimeException("MinIO 连接失败")).when(minioService)
                .uploadLocalFile(any(Path.class), anyString(), anyString());

        mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .param("title", "存储异常测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(containsString("视频上传失败")));

        verify(videoService, never()).save(any(Video.class));
    }

    @Test
    @DisplayName("TC-01-2 转码服务抛异常（异常流）：应返回 500 视频上传失败，且不保存视频")
    void uploadVideo_whenTranscodeThrows_shouldReturn500() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", "fake-video-content".getBytes());

        // MinIO 上传成功之后，转码环节抛异常（模拟 FFmpeg 失败）
        doThrow(new RuntimeException("FFmpeg 转码失败")).when(videoTranscodeService)
                .transcodeAndUpload(any(Path.class), anyString());

        mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .param("title", "转码失败测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(containsString("视频上传失败")));

        // 验证交互顺序：文件已上传到 MinIO，但视频记录没有入库
        verify(minioService).uploadLocalFile(any(Path.class), anyString(), anyString());
        verify(videoService, never()).save(any(Video.class));
    }

    @Test
    @DisplayName("TC-01-2 转码未产出清晰度文件（备选分支）：上传仍成功，url720p 回退为原始地址")
    void uploadVideo_whenTranscodeReturnsEmpty_shouldFallbackToOriginalUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", "fake-video-content".getBytes());

        // 转码不抛异常，但返回空 map（没有产出 480P/720P/1080P 文件）
        when(videoTranscodeService.transcodeAndUpload(any(Path.class), anyString()))
                .thenReturn(Map.of());

        // 用 ArgumentCaptor 捕获真正要入库的 Video 对象
        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        when(videoService.save(videoCaptor.capture())).thenReturn(true);

        Video savedVideo = new Video();
        savedVideo.setId(10L);
        savedVideo.setTitle("测试视频标题");
        savedVideo.setStatus("public");
        when(videoService.getVideoById(any())).thenReturn(savedVideo);

        mockMvc.perform(multipart("/api/videos/upload")
                        .file(file)
                        .param("title", "测试视频标题")
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("上传成功"));

        // 关键断言：转码无结果时，默认清晰度回退为原始播放地址
        Video captured = videoCaptor.getValue();
        assertNotNull(captured, "应当有 Video 对象被保存");
        assertTrue(captured.getUrl480p().isBlank(), "无转码结果时 480P 应为空");
        assertTrue(captured.getUrl1080p().isBlank(), "无转码结果时 1080P 应为空");
        assertEquals(captured.getPlayUrl(), captured.getUrl720p(), "无转码结果时 720P 应回退为原始播放地址");
    }

    // ==================== UC-02 管理个人视频 ====================

    @Test
    @DisplayName("TC-02-1 查看本人视频列表：主成功流程，应返回视频列表")
    void getUserUploads_shouldReturnVideoList() throws Exception {
        Video video = new Video();
        video.setId(1L);
        video.setTitle("我的第一个视频");
        video.setStatus("public");
        when(videoService.getVideosByUserId(100L)).thenReturn(List.of(video));

        mockMvc.perform(get("/api/videos/user/100/uploads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("我的第一个视频"));
    }

    @Test
    @DisplayName("TC-02-2 删除本人视频：主成功流程（请求头传 userId），应返回删除成功")
    void deleteVideo_whenOwnerDeletesViaHeader_shouldSucceed() throws Exception {
        when(videoService.deleteOwnVideo(100L, 1L)).thenReturn(true);

        mockMvc.perform(delete("/api/videos/1")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 控制器调用 ApiResponse.success("删除成功") 单参数重载，字符串在 data 字段
                .andExpect(jsonPath("$.data").value("删除成功"));
    }

    @Test
    @DisplayName("TC-02-2 删除本人视频（备选流程：请求体传 userId），应返回删除成功")
    void deleteVideo_whenOwnerDeletesViaBody_shouldSucceed() throws Exception {
        when(videoService.deleteOwnVideo(100L, 1L)).thenReturn(true);

        mockMvc.perform(delete("/api/videos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));
    }

    @Test
    @DisplayName("删除缺少用户身份（异常流）：应返回 400 缺少用户身份")
    void deleteVideo_whenNoIdentity_shouldReturn400() throws Exception {
        mockMvc.perform(delete("/api/videos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少用户身份"));

        verify(videoService, never()).deleteOwnVideo(any(), any());
    }

    @Test
    @DisplayName("TC-02-3 删除他人视频被拒绝（越权异常流）：应返回 403 无权删除该视频")
    void deleteVideo_whenNotOwner_shouldReturn403() throws Exception {
        when(videoService.deleteOwnVideo(100L, 1L)).thenReturn(false);

        mockMvc.perform(delete("/api/videos/1")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权删除该视频"));
    }

    @Test
    @DisplayName("查询不存在的视频（异常流）：应返回 404 视频不存在")
    void getVideoById_whenNotExists_shouldReturn404() throws Exception {
        when(videoService.getVideoById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/videos/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("视频不存在"));
    }

    @Test
    @DisplayName("设置视频可见性（备选流程）：本人设为仅自己可见，应返回成功")
    void setVisibility_whenOwner_shouldSucceed() throws Exception {
        when(videoService.setVisibility(100L, 1L, false)).thenReturn(true);

        mockMvc.perform(post("/api/videos/1/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 100, \"visible\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("已设为仅自己可见"));
    }

    @Test
    @DisplayName("设置视频可见性（越权异常流）：非本人操作应返回 403 无权操作该视频")
    void setVisibility_whenNotOwner_shouldReturn403() throws Exception {
        when(videoService.setVisibility(100L, 1L, false)).thenReturn(false);

        mockMvc.perform(post("/api/videos/1/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 100, \"visible\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该视频"));
    }
}
