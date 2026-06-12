package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.entity.LiveDanmu;
import com.example.demo.service.LiveDanmuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/live/rooms")
@RequiredArgsConstructor
public class LiveDanmuController {

    private final LiveDanmuService liveDanmuService;

    @GetMapping("/{roomId}/danmus")
    public ApiResponse<List<LiveDanmu>> getHistory(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(liveDanmuService.getRecentDanmu(roomId, limit));
    }
}
