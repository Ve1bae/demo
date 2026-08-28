package com.example.live.model;

import java.time.LocalDateTime;

public record LiveDanmuView(
        Long id,
        Long roomId,
        Long userId,
        String username,
        String content,
        String color,
        LocalDateTime sendTime
) {
}
