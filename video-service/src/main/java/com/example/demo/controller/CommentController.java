package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.entity.Comment;
import com.example.demo.entity.Video;
import com.example.demo.service.CommentService;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Long viewerId = headerUserId != null ? headerUserId : userId;
        Map<String, Object> result = commentService.getCommentsByVideoIdWithPagination(videoId, page, pageSize, viewerId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{videoId}/comments")
    public ResponseEntity<ApiResponse<String>> postComment(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        String content = (String) requestBody.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error(400, "评论内容不能为空"));
        }

        Comment comment = new Comment();
        comment.setVideoId(videoId);
        comment.setContent(content);

        Object parentId = requestBody.get("parentId");
        if (parentId != null && !"null".equals(parentId.toString())) {
            comment.setParentId(Long.parseLong(parentId.toString()));
        }

        Long userId = headerUserId;
        Object bodyUserId = requestBody.get("userId");
        if (userId == null && bodyUserId != null) {
            userId = Long.parseLong(bodyUserId.toString());
        }
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.error(401, "请先登录后再评论"));
        }
        comment.setUserId(userId);

        boolean success = commentService.saveComment(comment);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("评论发布成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(500, "评论发布失败"));
    }

    @PostMapping("/{videoId}/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<String>> likeComment(
            @PathVariable Long videoId,
            @PathVariable Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestBody(required = false) Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Long userId = headerUserId;
        Object bodyUserId = requestBody == null ? null : requestBody.get("userId");
        if (userId == null && bodyUserId != null) {
            userId = Long.parseLong(bodyUserId.toString());
        }
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.error(401, "请先登录后再点赞"));
        }

        boolean success = commentService.likeComment(commentId, userId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("点赞成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(409, "已经点赞过该评论"));
    }

    @DeleteMapping("/{videoId}/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<String>> unlikeComment(
            @PathVariable Long videoId,
            @PathVariable Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestBody(required = false) Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Long userId = headerUserId;
        Object bodyUserId = requestBody == null ? null : requestBody.get("userId");
        if (userId == null && bodyUserId != null) {
            userId = Long.parseLong(bodyUserId.toString());
        }
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.error(401, "请先登录后再取消点赞"));
        }

        boolean success = commentService.unlikeComment(commentId, userId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("取消点赞成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(409, "还没有点赞该评论"));
    }

    @DeleteMapping("/{videoId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<String>> deleteComment(
            @PathVariable Long videoId,
            @PathVariable Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestBody(required = false) Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        Long userId = headerUserId;
        Object bodyUserId = requestBody == null ? null : requestBody.get("userId");
        if (userId == null && bodyUserId != null) {
            userId = Long.parseLong(bodyUserId.toString());
        }
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.error(401, "请先登录后再删除评论"));
        }

        Comment comment = commentService.getCommentById(commentId);
        if (comment == null || !videoId.equals(comment.getVideoId())) {
            return ResponseEntity.ok(ApiResponse.error(404, "评论不存在"));
        }
        boolean isCommentOwner = userId.equals(comment.getUserId());
        boolean isVideoOwner = userId.equals(video.getUserId());
        if (!isCommentOwner && !isVideoOwner) {
            return ResponseEntity.ok(ApiResponse.error(403, "只能删除自己的评论，或删除自己视频下的评论"));
        }

        boolean success = commentService.deleteComment(commentId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("评论删除成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(500, "评论删除失败"));
    }
}
