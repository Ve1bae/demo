package com.example.interaction.model;

import java.time.LocalDateTime;

public record NotificationView(
        Long id,
        Long recipientUserId,
        Long actorUserId,
        String type,
        Long dynamicId,
        String content,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
}
