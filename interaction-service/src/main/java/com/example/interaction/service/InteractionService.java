package com.example.interaction.service;

import com.example.interaction.dto.CreateDynamicRequest;
import com.example.interaction.model.DynamicView;
import com.example.interaction.model.NotificationView;
import com.example.interaction.repository.InteractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class InteractionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_MENTION_COUNT = 20;

    private final InteractionRepository repository;

    public InteractionService(InteractionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DynamicView createDynamic(Long authorId, CreateDynamicRequest request) {
        if (authorId == null || authorId <= 0) {
            throw new IllegalArgumentException("用户身份不合法");
        }
        if (request == null || request.content() == null || request.content().trim().isBlank()) {
            throw new IllegalArgumentException("动态内容不能为空");
        }

        String content = request.content().trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("动态内容不能超过1000字");
        }
        if (request.mentionedUserIds() != null && request.mentionedUserIds().size() > MAX_MENTION_COUNT) {
            throw new IllegalArgumentException("一条动态最多提及20位用户");
        }
        List<Long> mentionedUserIds = normalizeMentionedUserIds(authorId, request.mentionedUserIds());
        return repository.insertDynamic(authorId, content, mentionedUserIds);
    }

    public List<DynamicView> getFeed(int limit, int offset) {
        return repository.findDynamics(null, normalizeLimit(limit), normalizeOffset(offset));
    }

    public List<DynamicView> getUserDynamics(Long authorId, int limit, int offset) {
        requirePositive(authorId, "用户 ID");
        return repository.findDynamics(authorId, normalizeLimit(limit), normalizeOffset(offset));
    }

    public List<NotificationView> getNotifications(Long recipientUserId, boolean unreadOnly, int limit, int offset) {
        requirePositive(recipientUserId, "用户 ID");
        return repository.findNotifications(
                recipientUserId,
                unreadOnly,
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    public boolean markNotificationRead(Long recipientUserId, Long notificationId) {
        requirePositive(recipientUserId, "用户 ID");
        requirePositive(notificationId, "提醒 ID");
        return repository.markNotificationRead(recipientUserId, notificationId);
    }

    public int countUnreadNotifications(Long recipientUserId) {
        requirePositive(recipientUserId, "用户 ID");
        return repository.countUnreadNotifications(recipientUserId);
    }

    private List<Long> normalizeMentionedUserIds(Long authorId, List<Long> mentionedUserIds) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long mentionedUserId : mentionedUserIds) {
            if (mentionedUserId == null || mentionedUserId <= 0) {
                throw new IllegalArgumentException("被提及用户 ID 不合法");
            }
            if (!mentionedUserId.equals(authorId)) {
                normalized.add(mentionedUserId);
            }
        }
        return new ArrayList<>(normalized);
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
    }

    private int normalizeOffset(int offset) {
        return Math.max(offset, 0);
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + "不合法");
        }
    }
}
