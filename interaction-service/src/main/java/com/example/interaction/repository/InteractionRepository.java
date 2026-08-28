package com.example.interaction.repository;

import com.example.interaction.model.DynamicView;
import com.example.interaction.model.NotificationView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class InteractionRepository {

    private final JdbcTemplate jdbcTemplate;

    public InteractionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DynamicView insertDynamic(Long authorId, String content, List<Long> mentionedUserIds) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO interaction_dynamic (author_id, content) VALUES (?, ?)",
                    new String[]{"id"});
            statement.setLong(1, authorId);
            statement.setString(2, content);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建动态后未返回动态 ID");
        }
        Long dynamicId = key.longValue();
        for (Long mentionedUserId : mentionedUserIds) {
            jdbcTemplate.update(
                    "INSERT INTO interaction_dynamic_mention (dynamic_id, mentioned_user_id) VALUES (?, ?)",
                    dynamicId,
                    mentionedUserId);
            jdbcTemplate.update(
                    "INSERT INTO interaction_notification "
                            + "(recipient_user_id, actor_user_id, type, dynamic_id, content) "
                            + "VALUES (?, ?, 'MENTION', ?, ?)",
                    mentionedUserId,
                    authorId,
                    dynamicId,
                    "用户 " + authorId + " 在动态中提到了你");
        }

        return new DynamicView(dynamicId, authorId, content, mentionedUserIds, LocalDateTime.now());
    }

    public List<DynamicView> findDynamics(Long authorId, int limit, int offset) {
        String sql = "SELECT d.id, d.author_id, d.content, d.created_at "
                + "FROM interaction_dynamic d "
                + (authorId == null ? "" : "WHERE d.author_id = ? ")
                + "ORDER BY d.created_at DESC, d.id DESC LIMIT ? OFFSET ?";
        Object[] args = authorId == null
                ? new Object[]{limit, offset}
                : new Object[]{authorId, limit, offset};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DynamicView(
                rs.getLong("id"),
                rs.getLong("author_id"),
                rs.getString("content"),
                findMentionedUserIds(rs.getLong("id")),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), args);
    }

    public List<NotificationView> findNotifications(Long recipientUserId, boolean unreadOnly, int limit, int offset) {
        String sql = "SELECT id, recipient_user_id, actor_user_id, type, dynamic_id, content, "
                + "is_read, created_at, read_at FROM interaction_notification "
                + "WHERE recipient_user_id = ? "
                + (unreadOnly ? "AND is_read = 0 " : "")
                + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new NotificationView(
                rs.getLong("id"),
                rs.getLong("recipient_user_id"),
                rs.getLong("actor_user_id"),
                rs.getString("type"),
                rs.getLong("dynamic_id"),
                rs.getString("content"),
                rs.getBoolean("is_read"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("read_at"))
        ), recipientUserId, limit, offset);
    }

    public boolean markNotificationRead(Long recipientUserId, Long notificationId) {
        return jdbcTemplate.update(
                "UPDATE interaction_notification SET is_read = 1, read_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND recipient_user_id = ? AND is_read = 0",
                notificationId,
                recipientUserId) > 0;
    }

    public int countUnreadNotifications(Long recipientUserId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interaction_notification WHERE recipient_user_id = ? AND is_read = 0",
                Integer.class,
                recipientUserId);
        return count == null ? 0 : count;
    }

    private List<Long> findMentionedUserIds(Long dynamicId) {
        return jdbcTemplate.queryForList(
                "SELECT mentioned_user_id FROM interaction_dynamic_mention "
                        + "WHERE dynamic_id = ? ORDER BY id",
                Long.class,
                dynamicId);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
