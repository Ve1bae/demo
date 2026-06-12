package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.VideoService;
import com.example.demo.service.impl.VideoServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final VideoServiceImpl videoServiceImpl;
    private final UserMapper userMapper;

    @Value("${app.upload-dir:backend/uploads}")
    private String uploadDir;

    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<List<Video>>> getRecommendVideos(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer pageSize,
            @RequestParam(defaultValue = "0") Integer categoryId,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        List<Video> videoList = videoService.getRecommendedFeed(currentUserId, page, pageSize, categoryId, keyword);
        return ResponseEntity.ok(ApiResponse.success(videoList));
    }

    @GetMapping("/{videoId}/related")
    public ResponseEntity<ApiResponse<List<Video>>> getRelatedVideos(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
            @RequestParam(defaultValue = "6") Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(videoService.getRecommendedVideos(videoId, currentUserId, limit)));
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Video>> getVideoById(@PathVariable Long videoId) {
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(video));
    }

    @DeleteMapping("/{videoId}")
    public ResponseEntity<ApiResponse<String>> deleteVideo(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "请先登录后删除稿件"));
        }

        boolean success = videoService.deleteOwnVideo(currentUser.getId(), videoId);
        if (!success) {
            return ResponseEntity.ok(ApiResponse.fail(403, "只能删除自己的稿件"));
        }
        return ResponseEntity.ok(ApiResponse.success("稿件已删除", null));
    }

    @GetMapping("/url")
    public ResponseEntity<ApiResponse<Video>> getVideoByVideoUrl(@RequestParam String videoUrl) {
        Video video = videoService.getVideoByVideoUrl(videoUrl);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(video));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Video>> uploadVideo(
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false, defaultValue = "") String coverUrl,
            @RequestParam(required = false, defaultValue = "") String duration,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false, defaultValue = "匿名用户") String author,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestParam("file") MultipartFile file) {

        if (!StringUtils.hasText(title)) {
            return ResponseEntity.ok(ApiResponse.error(400, "请填写视频标题"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "请先选择要上传的视频文件"));
        }

        try {
            String originalName = file.getOriginalFilename();
            String extension = getExtension(originalName);
            String storedName = "video-" + UUID.randomUUID() + extension;

            Path videoDir = Paths.get(uploadDir, "videos").toAbsolutePath().normalize();
            Files.createDirectories(videoDir);
            Path target = videoDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = "http://localhost:8080/uploads/videos/" + storedName;
            String resolvedCoverUrl = resolveCoverUrl(coverUrl, coverImage, title, author, storedName);

            Video video = new Video();
            video.setTitle(title);
            video.setDescription(description);
            video.setCoverUrl(resolvedCoverUrl);
            video.setAuthor(author);
            video.setDuration(StringUtils.hasText(duration) ? duration : null);
            video.setPlayUrl(publicUrl);
            video.setVideoUrl("video-" + UUID.randomUUID());
            video.setUrl720p(publicUrl);
            video.setDefaultQuality("720P");
            video.setPlayCount(0);
            video.setLikeCount(0);
            video.setFavoriteCount(0);
            video.setCommentCount(0);
            video.setCreatedAt(LocalDateTime.now());

            videoService.save(video);
            videoServiceImpl.decorateAndBindTags(video, tags);
            Video savedVideo = videoService.getVideoById(video.getId());
            return ResponseEntity.ok(ApiResponse.success("上传成功", savedVideo));
        } catch (IOException e) {
            return ResponseEntity.ok(ApiResponse.error(500, "上传视频失败: " + e.getMessage()));
        }
    }

    @PostMapping("/{videoId}/likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "请先登录后点赞视频"));
        }

        return ResponseEntity.ok(ApiResponse.success(videoService.toggleLike(currentUser.getId(), videoId)));
    }

    @DeleteMapping("/{videoId}/likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelLike(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "请先登录后取消点赞"));
        }

        return ResponseEntity.ok(ApiResponse.success(videoService.toggleLike(currentUser.getId(), videoId)));
    }

    @PostMapping("/{videoId}/favorites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFavorite(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "请先登录后收藏视频"));
        }

        return ResponseEntity.ok(ApiResponse.success(videoService.toggleFavorite(currentUser.getId(), videoId)));
    }

    @DeleteMapping("/{videoId}/favorites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelFavorite(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "请先登录后取消收藏"));
        }

        return ResponseEntity.ok(ApiResponse.success(videoService.toggleFavorite(currentUser.getId(), videoId)));
    }

    @PostMapping("/{videoId}/play")
    public ResponseEntity<ApiResponse<Map<String, Object>>> incrementPlayCount(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(videoService.incrementPlayCount(videoId, currentUserId)));
    }

    @PostMapping("/{videoId}/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> touchViewHistory(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(videoService.touchViewHistory(videoId, currentUserId)));
    }

    @PutMapping("/{videoId}/history/progress")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateViewHistoryProgress(
            @PathVariable Long videoId,
            @RequestParam Integer progressSeconds,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                videoService.updateViewHistoryProgress(videoId, currentUserId, progressSeconds)
        ));
    }

    @GetMapping("/{videoId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserVideoStatus(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        if (currentUserId == null) {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "liked", false,
                    "favorited", false,
                    "videoId", videoId
            )));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "当前登录用户不存在"));
        }

        return ResponseEntity.ok(ApiResponse.success(videoService.getUserVideoStatus(currentUser.getId(), videoId)));
    }

    private String generateAutoCover(String title, String author, String storedName) throws IOException {
        Path coverDir = Paths.get(uploadDir, "covers").toAbsolutePath().normalize();
        Files.createDirectories(coverDir);

        String coverName = storedName.replaceFirst("\\.[^.]+$", "") + ".png";
        Path coverPath = coverDir.resolve(coverName);

        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(15, 23, 42));
        graphics.fillRect(0, 0, 1280, 720);
        graphics.setColor(new Color(30, 64, 175));
        graphics.fillRoundRect(80, 80, 1120, 560, 48, 48);
        graphics.setColor(new Color(248, 250, 252));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 52));

        String safeTitle = StringUtils.hasText(title) ? title.trim() : "航音视频";
        drawCenteredLine(graphics, safeTitle, 1280, 300);

        graphics.setFont(new Font("SansSerif", Font.PLAIN, 32));
        String safeAuthor = StringUtils.hasText(author) ? "UP主：" + author.trim() : "航音视频";
        drawCenteredLine(graphics, safeAuthor, 1280, 390);

        graphics.setFont(new Font("SansSerif", Font.PLAIN, 28));
        graphics.setColor(new Color(191, 219, 254));
        drawCenteredLine(graphics, "自动生成封面", 1280, 500);
        graphics.dispose();

        ImageIO.write(image, "png", new File(coverPath.toString()));
        return "http://localhost:8080/uploads/covers/" + coverName;
    }

    private String resolveCoverUrl(String coverUrl, MultipartFile coverImage, String title, String author, String storedName) throws IOException {
        if (StringUtils.hasText(coverUrl)) {
            return coverUrl;
        }
        if (coverImage != null && !coverImage.isEmpty()) {
            Path coverDir = Paths.get(uploadDir, "covers").toAbsolutePath().normalize();
            Files.createDirectories(coverDir);
            String extension = getExtension(coverImage.getOriginalFilename());
            String coverName = storedName.replaceFirst("\\.[^.]+$", "") + (StringUtils.hasText(extension) ? extension : ".png");
            Path coverPath = coverDir.resolve(coverName);
            Files.copy(coverImage.getInputStream(), coverPath, StandardCopyOption.REPLACE_EXISTING);
            return "http://localhost:8080/uploads/covers/" + coverName;
        }
        return generateAutoCover(title, author, storedName);
    }

    private void drawCenteredLine(Graphics2D graphics, String text, int width, int baselineY) {
        FontMetrics metrics = graphics.getFontMetrics();
        int drawX = Math.max((width - metrics.stringWidth(text)) / 2, 40);
        graphics.drawString(text, drawX, baselineY);
    }

    private User validateCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userMapper.selectById(currentUserId);
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return ".mp4";
        }
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(index) : ".mp4";
    }
}
