package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.entity.Comment;
import com.example.demo.entity.User;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserMapper;
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
    private final UserMapper userMapper;

    @GetMapping("/{videoId}/comments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommentsByVideoId(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Map<String, Object> result =
                commentService.getCommentsByVideoIdWithPagination(videoId, page, pageSize, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{videoId}/comments")
    public ResponseEntity<ApiResponse<String>> postComment(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "请先登录后发表评论"));
        }

        String content = requestBody.get("content") == null ? null : requestBody.get("content").toString().trim();
        if (content == null || content.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.fail(400, "评论内容不能为空"));
        }

        Comment comment = new Comment();
        comment.setVideoId(videoId);
        comment.setContent(content);
        comment.setUserId(currentUser.getId());

        Object parentId = requestBody.get("parentId");
        if (parentId != null && !"null".equals(parentId.toString())) {
            comment.setParentId(Long.parseLong(parentId.toString()));
        }

        boolean success = commentService.saveComment(comment);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("评论发布成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(500, "评论发布失败"));
    }

    @PostMapping("/{videoId}/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Map<String, Object>>> likeComment(
            @PathVariable Long videoId,
            @PathVariable Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "请先登录后点赞评论"));
        }

        Comment comment = commentService.getCommentById(commentId);
        if (comment == null || !videoId.equals(comment.getVideoId())) {
            return ResponseEntity.ok(ApiResponse.fail(404, "评论不存在"));
        }

        Map<String, Object> result = commentService.likeComment(commentId, currentUser.getId());
        String message = Boolean.TRUE.equals(result.get("liked")) ? "评论点赞成功" : "已取消评论点赞";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @DeleteMapping("/{videoId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<String>> deleteComment(
            @PathVariable Long videoId,
            @PathVariable Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        User currentUser = validateCurrentUser(currentUserId);
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.fail(401, "请先登录后删除评论"));
        }

        Comment comment = commentService.getCommentById(commentId);
        if (comment == null || !videoId.equals(comment.getVideoId())) {
            return ResponseEntity.ok(ApiResponse.fail(404, "评论不存在"));
        }
        if (!currentUser.getId().equals(comment.getUserId())) {
            return ResponseEntity.ok(ApiResponse.fail(403, "只能删除自己的评论"));
        }

        boolean success = commentService.deleteComment(commentId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("评论删除成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(500, "评论删除失败"));
    }

    private User validateCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userMapper.selectById(currentUserId);
    }
}
