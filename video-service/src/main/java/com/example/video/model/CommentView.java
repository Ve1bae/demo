package com.example.video.model;

import java.time.LocalDateTime;

public record CommentView(
        Long id,
        Long videoId,
        Long userId,
        String username,
        String content,
        Long parentId,
        Integer likeCount,
        Boolean liked,
        LocalDateTime createdAt
) {
}
