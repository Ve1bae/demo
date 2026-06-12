package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.entity.Video;
import com.example.demo.service.MinioService;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final MinioService minioService;

    @Value("${minio.public-base-url:http://localhost:8082/video}")
    private String minioPublicBaseUrl;

    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<List<Video>>> getRecommendVideos(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer pageSize,
            @RequestParam(defaultValue = "0") Integer categoryId,
            @RequestParam(required = false) String keyword) {

        List<Video> videoList = videoService.getAllVideos();
        return ResponseEntity.ok(ApiResponse.success(videoList));
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Video>> getVideoById(@PathVariable Long videoId) {
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(video));
    }

    @GetMapping("/url")
    public ResponseEntity<ApiResponse<Video>> getVideoByVideoUrl(@RequestParam String videoUrl) {
        Video video = videoService.getVideoByVideoUrl(videoUrl);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(video));
    }

    @GetMapping("/user/{userId}/uploads")
    public ResponseEntity<ApiResponse<List<Video>>> getUserUploads(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(videoService.getVideosByUserId(userId)));
    }

    @GetMapping("/user/{userId}/favorites")
    public ResponseEntity<ApiResponse<List<Video>>> getUserFavorites(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(videoService.getFavoriteVideosByUserId(userId)));
    }

    @PostMapping("/{videoId}/visibility")
    public ResponseEntity<ApiResponse<String>> setVisibility(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {
        Long userId = Long.parseLong(requestBody.get("userId").toString());
        boolean visible = Boolean.parseBoolean(requestBody.get("visible").toString());
        boolean success = videoService.setVisibility(userId, videoId, visible);
        if (!success) {
            return ResponseEntity.ok(ApiResponse.error(403, "无权操作该视频"));
        }
        return ResponseEntity.ok(ApiResponse.success(visible ? "已设为公开" : "已设为仅自己可见"));
    }

    @DeleteMapping("/{videoId}")
    public ResponseEntity<ApiResponse<String>> deleteVideo(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {
        Long userId = Long.parseLong(requestBody.get("userId").toString());
        boolean success = videoService.deleteOwnVideo(userId, videoId);
        if (!success) {
            return ResponseEntity.ok(ApiResponse.error(403, "无权删除该视频"));
        }
        return ResponseEntity.ok(ApiResponse.success("删除成功"));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Video>> uploadVideo(
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false, defaultValue = "") String coverUrl,
            @RequestParam(required = false, defaultValue = "匿名用户") String author,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer duration,
            @RequestParam("file") MultipartFile file) {

        if (!StringUtils.hasText(title)) {
            return ResponseEntity.ok(ApiResponse.error(400, "视频标题不能为空"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "请选择要上传的视频文件"));
        }

        try {
            String originalName = file.getOriginalFilename();
            String extension = getExtension(originalName);
            String storedName = "video-" + UUID.randomUUID() + extension;
            String objectName = "videos/" + storedName;
            minioService.uploadFile(file, objectName);

            String publicUrl = minioPublicBaseUrl.replaceAll("/+$", "") + "/" + objectName;

            Video video = new Video();
            video.setTitle(title);
            video.setDescription(description);
            video.setCoverUrl(coverUrl);
            video.setAuthor(author);
            video.setUserId(userId);
            video.setDuration(duration);
            video.setStatus("public");
            video.setPlayUrl(publicUrl);
            video.setVideoUrl(objectName);
            video.setUrl720p(publicUrl);
            video.setDefaultQuality("720P");
            video.setPlayCount(0);
            video.setLikeCount(0);
            video.setFavoriteCount(0);
            video.setCommentCount(0);
            video.setCreatedAt(LocalDateTime.now());

            videoService.save(video);
            Video savedVideo = videoService.getVideoById(video.getId());
            return ResponseEntity.ok(ApiResponse.success("上传成功", savedVideo));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "视频上传失败: " + e.getMessage()));
        }
    }

    @PostMapping("/{videoId}/likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        Long userId = Long.parseLong(requestBody.get("userId").toString());
        return ResponseEntity.ok(ApiResponse.success(videoService.toggleLike(userId, videoId)));
    }

    @DeleteMapping("/{videoId}/likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelLike(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        Long userId = Long.parseLong(requestBody.get("userId").toString());
        return ResponseEntity.ok(ApiResponse.success(videoService.toggleLike(userId, videoId)));
    }

    @PostMapping("/{videoId}/favorites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFavorite(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        Long userId = Long.parseLong(requestBody.get("userId").toString());
        return ResponseEntity.ok(ApiResponse.success(videoService.toggleFavorite(userId, videoId)));
    }

    @DeleteMapping("/{videoId}/favorites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelFavorite(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        Long userId = Long.parseLong(requestBody.get("userId").toString());
        return ResponseEntity.ok(ApiResponse.success(videoService.toggleFavorite(userId, videoId)));
    }

    @PostMapping("/{videoId}/play")
    public ResponseEntity<ApiResponse<Map<String, Object>>> incrementPlayCount(@PathVariable Long videoId) {
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(videoService.incrementPlayCount(videoId)));
    }

    @GetMapping("/{videoId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserVideoStatus(
            @PathVariable Long videoId,
            @RequestParam Long userId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(videoService.getUserVideoStatus(userId, videoId)));
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return ".mp4";
        }
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(index) : ".mp4";
    }
}
