package com.example.live.controller;

import com.example.live.common.ApiResponse;
import com.example.live.common.PageResult;
import com.example.live.dto.CreateLiveRoomRequest;
import com.example.live.model.LiveDanmuView;
import com.example.live.model.LiveRoomView;
import com.example.live.service.LiveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/live")
public class LiveController {
    private final LiveService liveService;

    public LiveController(LiveService liveService) {
        this.liveService = liveService;
    }

    @GetMapping("/rooms")
    public ApiResponse<PageResult<LiveRoomView>> listRooms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(liveService.listRooms(page, pageSize, categoryId));
    }

    @PostMapping("/rooms")
    public ApiResponse<LiveRoomView> createRoom(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody CreateLiveRoomRequest request) {
        return ApiResponse.success("直播间创建成功", liveService.createRoom(userId, request));
    }

    @GetMapping("/rooms/{roomId}")
    public ApiResponse<LiveRoomView> getRoom(@PathVariable Long roomId) {
        return ApiResponse.success(liveService.getRoom(roomId));
    }

    @PostMapping("/rooms/{roomId}/close")
    public ApiResponse<LiveRoomView> closeRoom(
            @PathVariable Long roomId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success("直播已结束", liveService.closeRoom(roomId, userId));
    }

    @GetMapping("/rooms/{roomId}/danmus")
    public ApiResponse<List<LiveDanmuView>> getDanmus(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(liveService.getDanmus(roomId, limit));
    }

    @GetMapping("/rooms/{roomId}/like")
    public ApiResponse<Map<String, Long>> getLikeCount(@PathVariable Long roomId) {
        return ApiResponse.success(Map.of("likeCount", liveService.getLikeCount(roomId)));
    }

    @GetMapping("/srs/health")
    public ApiResponse<Map<String, Object>> getSrsHealth() {
        return ApiResponse.success(liveService.srsHealth());
    }
}
