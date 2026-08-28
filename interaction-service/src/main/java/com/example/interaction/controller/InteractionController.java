package com.example.interaction.controller;

import com.example.interaction.common.ApiResponse;
import com.example.interaction.dto.CreateDynamicRequest;
import com.example.interaction.model.DynamicView;
import com.example.interaction.model.NotificationView;
import com.example.interaction.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/dynamics")
    public ApiResponse<DynamicView> createDynamic(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody CreateDynamicRequest request) {
        requireUser(userId);
        return ApiResponse.success("动态发布成功", interactionService.createDynamic(userId, request));
    }

    @GetMapping("/dynamics")
    public ApiResponse<List<DynamicView>> getFeed(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success(interactionService.getFeed(limit, offset));
    }

    @GetMapping("/users/{userId}/dynamics")
    public ApiResponse<List<DynamicView>> getUserDynamics(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success(interactionService.getUserDynamics(userId, limit, offset));
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationView>> getNotifications(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        requireUser(userId);
        return ApiResponse.success(interactionService.getNotifications(userId, unreadOnly, limit, offset));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<Map<String, Integer>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        Map<String, Integer> result = new HashMap<>();
        result.put("unreadCount", interactionService.countUnreadNotifications(userId));
        return ApiResponse.success(result);
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<Void> markNotificationRead(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long notificationId) {
        requireUser(userId);
        boolean updated = interactionService.markNotificationRead(userId, notificationId);
        return updated
                ? ApiResponse.success("提醒已读", null)
                : ApiResponse.error(404, "提醒不存在或已经读过");
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
    }
}
