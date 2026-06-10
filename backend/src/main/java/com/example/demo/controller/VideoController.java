package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.Video;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /**
     * 点赞视频
     * POST /api/videos/{videoId}/likes
     * 请求体：{ "userId": 1 }
     */
    @PostMapping("/{videoId}/likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {
        
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Long userId = Long.parseLong(requestBody.get("userId").toString());
        Map<String, Object> result = videoService.toggleLike(userId, videoId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 取消点赞视频
     * DELETE /api/videos/{videoId}/likes
     * 请求体：{ "userId": 1 }
     */
    @DeleteMapping("/{videoId}/likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelLike(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {
        
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Long userId = Long.parseLong(requestBody.get("userId").toString());
        Map<String, Object> result = videoService.toggleLike(userId, videoId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 收藏视频
     * POST /api/videos/{videoId}/favorites
     * 请求体：{ "userId": 1 }
     */
    @PostMapping("/{videoId}/favorites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFavorite(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {
        
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Long userId = Long.parseLong(requestBody.get("userId").toString());
        Map<String, Object> result = videoService.toggleFavorite(userId, videoId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 取消收藏视频
     * DELETE /api/videos/{videoId}/favorites
     * 请求体：{ "userId": 1 }
     */
    @DeleteMapping("/{videoId}/favorites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelFavorite(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {
        
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Long userId = Long.parseLong(requestBody.get("userId").toString());
        Map<String, Object> result = videoService.toggleFavorite(userId, videoId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 增加播放量
     * POST /api/videos/{videoId}/play
     */
    @PostMapping("/{videoId}/play")
    public ResponseEntity<ApiResponse<Map<String, Object>>> incrementPlayCount(@PathVariable Long videoId) {
        
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Map<String, Object> result = videoService.incrementPlayCount(videoId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取用户对视频的点赞和收藏状态
     * GET /api/videos/{videoId}/status?userId=1
     */
    @GetMapping("/{videoId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserVideoStatus(
            @PathVariable Long videoId,
            @RequestParam Long userId) {
        
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Map<String, Object> result = videoService.getUserVideoStatus(userId, videoId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}