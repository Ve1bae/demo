package com.example.video.controller;

import com.example.video.common.ApiResponse;
import com.example.video.common.PageResult;
import com.example.video.dto.CommentRequest;
import com.example.video.service.CommentService;
import com.example.video.vo.CommentVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequestMapping("/api")
public class CommentController {
    private final CommentService comments;
    public CommentController(CommentService comments) { this.comments = comments; }

    @GetMapping("/videos/{videoId}/comments") public ApiResponse<PageResult<CommentVO>> list(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(comments.list(videoId, page, pageSize, userId));
    }

    @PostMapping("/videos/{videoId}/comments") public ApiResponse<CommentVO> add(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Username", required = false) String username,
            @RequestBody CommentRequest request) {
        return ApiResponse.success("评论成功", comments.add(videoId, userId, username, request.getContent(), request.getParentId()));
    }

    @PostMapping({"/comments/{commentId}/likes", "/videos/{videoId}/comments/{commentId}/like"}) public ApiResponse<Map<String, Object>> like(
            @PathVariable(value = "videoId", required = false) Long videoId,
            @PathVariable("commentId") Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(comments.like(commentId, userId));
    }

    @DeleteMapping({"/comments/{commentId}/likes", "/videos/{videoId}/comments/{commentId}/like"}) public ApiResponse<Map<String, Object>> unlike(
            @PathVariable(value = "videoId", required = false) Long videoId,
            @PathVariable("commentId") Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success(comments.unlike(commentId, userId));
    }
}
