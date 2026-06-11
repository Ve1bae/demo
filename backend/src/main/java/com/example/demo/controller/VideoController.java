package com.example.demo.controller;

import com.example.demo.common.R;
import com.example.demo.dto.ChunkInitResult;
import com.example.demo.dto.PagedResult;
import com.example.demo.dto.UploadProgress;
import com.example.demo.entity.Video;
import com.example.demo.service.MinioStorageService;
import com.example.demo.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    @Autowired
    private VideoService videoService;

    @Autowired
    private MinioStorageService minioStorageService;

    // ==================== 1. 初始化分片上传 ====================

    @PostMapping("/chunk-init")
    public R<?> initChunk(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        String title = (String) body.get("title");
        String description = (String) body.getOrDefault("description", "");
        Long categoryId = Long.valueOf(body.getOrDefault("categoryId", 0).toString());
        String fileName = (String) body.get("fileName");
        long fileSize = Long.parseLong(body.getOrDefault("fileSize", 0).toString());
        String contentType = (String) body.getOrDefault("contentType", "video/mp4");

        Video video = videoService.initUpload(userId, title, description, categoryId, fileName, fileSize, contentType);

        ChunkInitResult initResult = minioStorageService.initChunkUpload(video.getPlayUrl(), contentType);

        return R.ok(Map.of(
                "videoId", video.getId(),
                "uploadId", initResult.getUploadId(),
                "objectName", initResult.getObjectName(),
                "uploadedParts", initResult.getUploadedParts(),
                "chunkSize", 5 * 1024 * 1024
        ));
    }

    // ==================== 2. 上传单个分片 ====================

    @PostMapping("/chunk-upload")
    public R<?> uploadChunk(@RequestParam("file") MultipartFile chunk,
                            @RequestParam("videoId") Long videoId,
                            @RequestParam("chunkIndex") int chunkIndex,
                            @RequestParam("totalChunks") int totalChunks,
                            @RequestParam("uploadId") String uploadId,
                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Video video = videoService.getById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            return R.error(403, "无权操作");
        }

        try {
            minioStorageService.uploadChunk(
                    video.getPlayUrl(),
                    uploadId,
                    chunkIndex + 1,
                    chunk.getInputStream(),
                    chunk.getSize(),
                    chunk.getContentType()
            );
            return R.ok(Map.of("chunkIndex", chunkIndex, "uploaded", true));
        } catch (Exception e) {
            return R.error(500, "分片上传失败: " + e.getMessage());
        }
    }

    // ==================== 3. 合并分片 ====================

    @PostMapping("/chunk-complete")
    public R<?> completeChunk(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Long videoId = Long.valueOf(body.get("videoId").toString());
        String uploadId = (String) body.get("uploadId");

        Video video = videoService.getById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            return R.error(403, "无权操作");
        }

        int totalChunks = Integer.parseInt(body.getOrDefault("totalChunks", 0).toString());
        List<Integer> sortedParts = new ArrayList<>();
        for (int i = 1; i <= totalChunks; i++) {
            sortedParts.add(i);
        }

        String url = minioStorageService.completeChunkUpload(video.getPlayUrl(), uploadId, sortedParts);
        videoService.completeUpload(videoId, url);

        return R.ok(Map.of(
                "videoId", videoId,
                "playUrl", url,
                "status", "published"
        ));
    }

    // ==================== 4. 查询进度（断点续传） ====================

    @GetMapping("/chunk-progress/{videoId}")
    public R<?> getProgress(@PathVariable Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Video video = videoService.getById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            return R.error(403, "无权操作");
        }

        UploadProgress progress = minioStorageService.getUploadProgress(video.getPlayUrl());
        return R.ok(progress);
    }

    // ==================== 5. 小文件直接上传 ====================

    @PostMapping("/upload-small")
    public R<?> uploadSmallFile(@RequestParam("file") MultipartFile file,
                                @RequestParam("title") String title,
                                @RequestParam(value = "description", defaultValue = "") String description,
                                @RequestParam(value = "categoryId", defaultValue = "0") Long categoryId,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        String objectName = "videos/" + userId + "/" + System.currentTimeMillis() + "-" + file.getOriginalFilename();

        try {
            String url = minioStorageService.uploadSmallFile(
                    objectName, file.getInputStream(), file.getSize(), file.getContentType());

            Video video = new Video();
            video.setUserId(userId);
            video.setTitle(title);
            video.setDescription(description);
            video.setCategoryId(categoryId);
            video.setPlayUrl(objectName);
            video.setStatus("published");
            video.setCreatedAt(java.time.LocalDateTime.now());
            video.setUpdatedAt(java.time.LocalDateTime.now());
            video.setPlayCount(0L);
            video.setLikeCount(0L);
            video.setFavoriteCount(0L);
            video.setCommentCount(0L);
            video.setDuration(0);
            videoService.save(video);

            return R.ok(Map.of(
                    "videoId", video.getId(),
                    "playUrl", url
            ));
        } catch (Exception e) {
            return R.error(500, "上传失败: " + e.getMessage());
        }
    }

    // ==================== 6. 我的视频列表 ====================

    @GetMapping("/my")
    public R<?> getMyVideos(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int pageSize,
                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        PagedResult<Video> result = videoService.getMyVideos(userId, page, pageSize);
        return R.ok(Map.of(
                "list", result.getList(),
                "total", result.getTotal(),
                "page", page,
                "pageSize", pageSize
        ));
    }

    // ==================== 7. 删除视频 ====================

    @DeleteMapping("/{videoId}")
    public R<?> deleteVideo(@PathVariable Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            videoService.deleteVideo(userId, videoId);
            return R.ok();
        } catch (Exception e) {
            return R.error(500, "删除失败: " + e.getMessage());
        }
    }

    // ==================== 8. 上传封面 ====================

    @PostMapping("/upload-cover")
    public R<?> uploadCover(@RequestParam("file") MultipartFile file,
                            @RequestParam("videoId") Long videoId,
                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Video video = videoService.getById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            return R.error(403, "无权操作");
        }

        try {
            String coverObjectName = "covers/" + userId + "/" + videoId + "-" + file.getOriginalFilename();
            String url = minioStorageService.uploadSmallFile(
                    coverObjectName, file.getInputStream(), file.getSize(), file.getContentType());
            video.setCoverUrl(coverObjectName);
            videoService.updateById(video);
            return R.ok(Map.of("coverUrl", url));
        } catch (Exception e) {
            return R.error(500, "封面上传失败: " + e.getMessage());
        }
    }
}
