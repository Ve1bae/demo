package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.Video;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    /**
     * 获取推荐视频列表
     * GET /api/videos/recommend?page=1&pageSize=12&categoryId=0&keyword=
     */
    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<List<Video>>> getRecommendVideos(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer pageSize,
            @RequestParam(defaultValue = "0") Integer categoryId,
            @RequestParam(required = false) String keyword) {
        
        List<Video> videoList = videoService.getAllVideos();
        return ResponseEntity.ok(ApiResponse.success(videoList));
    }

    /**
     * 获取视频详情
     * GET /api/videos/{videoId}
     */
    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Video>> getVideoById(@PathVariable Long videoId) {
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(video));
    }

    /**
     * 根据视频URL获取视频信息（保留用于兼容旧接口）
     */
    @GetMapping("/url")
    public ResponseEntity<ApiResponse<Video>> getVideoByVideoUrl(@RequestParam String videoUrl) {
        Video video = videoService.getVideoByVideoUrl(videoUrl);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(video));
    }
}