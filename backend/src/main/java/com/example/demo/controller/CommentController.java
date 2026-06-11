package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
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

        Object parentId = requestBody.get("parentId");
        if (parentId != null && !"null".equals(parentId.toString())) {
            comment.setParentId(Long.parseLong(parentId.toString()));
        }

        Object userId = requestBody.get("userId");
        comment.setUserId(userId != null ? Long.parseLong(userId.toString()) : 1L);

        boolean success = commentService.saveComment(comment);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("评论发布成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(500, "评论发布失败"));
    }

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
            return ResponseEntity.ok(ApiResponse.success("点赞成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(500, "点赞失败"));
    }

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
            return ResponseEntity.ok(ApiResponse.success("评论删除成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(500, "评论删除失败"));
    }
}
