package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.Comment;
import com.example.demo.entity.Video;
import com.example.demo.service.CommentService;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final VideoService videoService;

    /**
     * 获取视频评论列表
     * GET /api/videos/{videoId}/comments?page=1&pageSize=20
     */
    @GetMapping("/{videoId}/comments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommentsByVideoId(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Map<String, Object> result = commentService.getCommentsByVideoIdWithPagination(videoId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 发布评论
     * POST /api/videos/{videoId}/comments
     * 请求体：{ "content": "评论内容", "parentId": null }
     */
    @PostMapping("/{videoId}/comments")
    public ResponseEntity<ApiResponse<String>> postComment(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Comment comment = new Comment();
        comment.setVideoId(videoId);
        comment.setContent((String) requestBody.get("content"));

        // 支持 parentId 字段（可选，用于回复评论）
        Object parentId = requestBody.get("parentId");
        if (parentId != null && !parentId.toString().equals("null")) {
            comment.setParentId(Long.parseLong(parentId.toString()));
        }

        // 支持 userId 字段（可选）
        Object userId = requestBody.get("userId");
        if (userId != null) {
            comment.setUserId(Long.parseLong(userId.toString()));
        } else {
            comment.setUserId(1L); // 默认用户ID
        }

        boolean success = commentService.saveComment(comment);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("评论发布成功"));
        } else {
            return ResponseEntity.ok(ApiResponse.error(500, "评论发布失败"));
        }
    }

    /**
     * 点赞评论
     * POST /api/videos/{videoId}/comments/{commentId}/like
     */
    @PostMapping("/{videoId}/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<String>> likeComment(
            @PathVariable Long videoId,
            @PathVariable Long commentId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        boolean success = commentService.likeComment(commentId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("点赞成功"));
        } else {
            return ResponseEntity.ok(ApiResponse.error(500, "点赞失败"));
        }
    }

    /**
     * 删除评论
     * DELETE /api/videos/{videoId}/comments/{commentId}
     */
    @DeleteMapping("/{videoId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<String>> deleteComment(
            @PathVariable Long videoId,
            @PathVariable Long commentId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        boolean success = commentService.deleteComment(commentId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("评论删除成功"));
        } else {
            return ResponseEntity.ok(ApiResponse.error(500, "评论删除失败"));
        }
    }
}