package com.example.video.controller;

import com.example.video.common.ApiResponse;
import com.example.video.dto.VisibilityRequest;
import com.example.video.service.VideoService;
import com.example.video.vo.VideoVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/videos")
public class VideoController {
    private final VideoService videos;
    public VideoController(VideoService videos) { this.videos = videos; }

    @GetMapping("/recommend") public ApiResponse<List<VideoVO>> recommend(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(videos.recommend(page, pageSize, categoryId, keyword, userId));
    }

    @GetMapping("/{videoId}") public ApiResponse<VideoVO> get(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(videos.getByIdWithState(videoId, userId));
    }

    @GetMapping("/user/{userId}/uploads") public ApiResponse<List<VideoVO>> uploads(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long viewer) {
        return ApiResponse.success(videos.getByUserId(userId, viewer));
    }

    @GetMapping("/user/{userId}/favorites") public ApiResponse<List<VideoVO>> favorites(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long viewer) {
        return ApiResponse.success(videos.getFavoritesByUserId(userId, viewer));
    }

    @GetMapping("/user/{userId}/history") public ApiResponse<List<Map<String,Object>>> history(@PathVariable Long userId) {
        return ApiResponse.success(videos.history(userId));
    }

    @PostMapping("/upload") public ApiResponse<VideoVO> upload(
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false, defaultValue = "") String coverUrl,
            @RequestParam(required = false, defaultValue = "") String tags,
            @RequestParam(required = false, defaultValue = "匿名用户") String author,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer duration,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("上传成功", videos.upload(file, title, description, coverUrl, tags, author, userId, duration));
    }

    @DeleteMapping("/{videoId}") public ApiResponse<?> delete(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (!videos.softDelete(videoId, userId)) return ApiResponse.error(403, "无权删除该视频");
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{videoId}/visibility") public ApiResponse<?> visibility(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody VisibilityRequest request) {
        boolean visible = Boolean.TRUE.equals(request.getVisible());
        if (!videos.setVisibility(videoId, userId, visible)) return ApiResponse.error(403, "无权操作该视频");
        return ApiResponse.success(visible ? "已设为公开" : "已设为仅自己可见", null);
    }

    @PostMapping("/{videoId}/play") public ApiResponse<Map<String, Object>> play(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(videos.play(videoId, userId));
    }

    @PostMapping("/{videoId}/likes") public ApiResponse<Map<String, Object>> like(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(videos.toggleLike(userId, videoId));
    }

    @DeleteMapping("/{videoId}/likes") public ApiResponse<Map<String, Object>> unlike(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(videos.toggleLike(userId, videoId));
    }

    @PostMapping("/{videoId}/favorites") public ApiResponse<Map<String, Object>> favorite(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(videos.toggleFavorite(userId, videoId));
    }

    @DeleteMapping("/{videoId}/favorites") public ApiResponse<Map<String, Object>> unfavorite(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(videos.toggleFavorite(userId, videoId));
    }

    @GetMapping("/{videoId}/status") public ApiResponse<Map<String, Object>> status(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(videos.status(userId, videoId));
    }
}
