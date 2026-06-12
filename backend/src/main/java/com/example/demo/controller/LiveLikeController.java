package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.service.RoomLikesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/live/rooms")
@RequiredArgsConstructor
public class LiveLikeController {

    private final RoomLikesService roomLikesService;

    @GetMapping("/{roomId}/like")
    public ApiResponse<Map<String, Long>> getLikeCount(@PathVariable Long roomId) {
        return ApiResponse.success(Map.of("likeCount", roomLikesService.getLikeCount(roomId)));
    }
}
