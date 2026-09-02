package com.example.video.controller;

import com.example.video.common.ApiResponse;
import com.example.video.dto.DanmakuRequest;
import com.example.video.service.DanmakuService;
import com.example.video.vo.DanmakuVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController @RequestMapping("/api/videos/{videoId}/danmakus")
public class DanmakuController {
    private final DanmakuService danmakus;
    public DanmakuController(DanmakuService danmakus) { this.danmakus = danmakus; }

    @GetMapping public ApiResponse<List<DanmakuVO>> list(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(danmakus.list(videoId, limit));
    }

    @PostMapping public ApiResponse<DanmakuVO> add(
            @PathVariable Long videoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Username", required = false) String username,
            @RequestBody DanmakuRequest request) {
        return ApiResponse.success("弹幕发送成功", danmakus.add(videoId, userId, username, request.getContent(), request.getColor(), request.getTimeSeconds()));
    }
}
