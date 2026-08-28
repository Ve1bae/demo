package com.example.interaction.model;

import java.time.LocalDateTime;
import java.util.List;

public record DynamicView(
        Long id,
        Long authorId,
        String content,
        List<Long> mentionedUserIds,
        LocalDateTime createdAt
) {
}
