package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.entity.Danmu;
import com.example.demo.service.DanmuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/danmu")
public class DanmuController {
    private final DanmuService danmuService;

    public DanmuController(DanmuService danmuService) {
        this.danmuService = danmuService;
    }

    @GetMapping("/history/{roomId}")
    public ApiResponse<List<Danmu>> getHistory(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(danmuService.getRecentDanmu(roomId, limit));
    }
}
