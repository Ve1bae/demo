package com.example.video.model;

import java.time.LocalDateTime;

public record DanmakuView(
        Long id,
        Long videoId,
        Long userId,
        String username,
        String content,
        String color,
        Integer timeSeconds,
        LocalDateTime createdAt
) {
}
