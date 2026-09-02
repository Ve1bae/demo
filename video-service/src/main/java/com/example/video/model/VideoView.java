package com.example.video.model;

import java.time.LocalDateTime;
import java.util.Map;

public record VideoView(
        Long id,
        String title,
        String description,
        String coverUrl,
        String playUrl,
        String author,
        Long userId,
        Integer categoryId,
        String tags,
        Integer duration,
        String status,
        Integer playCount,
        Integer likeCount,
        Integer favoriteCount,
        Integer commentCount,
        String defaultQuality,
        String views,
        Map<String, String> sources,
        Boolean liked,
        Boolean favorited,
        LocalDateTime createdAt
) {
}
