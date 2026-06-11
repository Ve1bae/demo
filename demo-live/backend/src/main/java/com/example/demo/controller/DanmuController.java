package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.entity.Danmu;
import com.example.demo.service.DanmuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/danmu")
public class DanmuController {

    @Autowired
    private DanmuService danmuService;

    @GetMapping("/history/{roomId}")
    public ApiResponse<List<Danmu>> getHistory(@PathVariable String roomId,
                                               @RequestParam(defaultValue = "50") int limit) {
        List<Danmu> list = danmuService.getRecentDanmu(roomId, limit);
        return ApiResponse.success(list);
    }
}