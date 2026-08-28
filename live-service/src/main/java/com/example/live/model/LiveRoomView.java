package com.example.live.model;

import java.time.LocalDateTime;
import java.util.Map;

public record LiveRoomView(
        Long roomId,
        Long userId,
        Long categoryId,
        String title,
        String streamName,
        String pushUrl,
        String pullUrl,
        Map<String, String> qualityUrls,
        String coverUrl,
        String status,
        Boolean streamActive,
        LocalDateTime createdAt
) {
}
