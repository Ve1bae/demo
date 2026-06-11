package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.service.RoomLikesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/live")
public class LiveLikeController {

    @Autowired
    private RoomLikesService roomLikesService;

    @GetMapping("/rooms/{roomId}/like")
    public ApiResponse<Map<String, Long>> getLikeCount(@PathVariable String roomId) {
        long count = roomLikesService.getLikeCount(roomId);
        Map<String, Long> data = new HashMap<>();
        data.put("likeCount", count);
        return ApiResponse.success(data);
    }
}