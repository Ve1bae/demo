package com.example.live.repository;

import com.example.live.model.LiveDanmuView;
import com.example.live.model.LiveRoomView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LiveRepository {
    private final JdbcTemplate jdbcTemplate;

    public LiveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insertRoom(Long userId, Long categoryId, String title, String streamName,
                           String pushUrl, String playUrl, String coverUrl) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO live_room "
                            + "(user_id, category_id, title, stream_name, push_url, play_url, cover_url, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'online')",
                    new String[]{"id"});
            statement.setLong(1, userId);
            if (categoryId == null) statement.setObject(2, null);
            else statement.setLong(2, categoryId);
            statement.setString(3, title);
            statement.setString(4, streamName);
            statement.setString(5, pushUrl);
            statement.setString(6, playUrl);
            statement.setString(7, coverUrl);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("创建直播间后未返回直播间 ID");
        return key.longValue();
    }

    public LiveRoomView findRoom(Long roomId) {
        List<LiveRoomView> rooms = jdbcTemplate.query(
                "SELECT id, user_id, category_id, title, stream_name, push_url, play_url, cover_url, status, create_time "
                        + "FROM live_room WHERE id = ?",
                (rs, rowNum) -> new LiveRoomView(
                        rs.getLong("id"), rs.getLong("user_id"),
                        (Long) rs.getObject("category_id"), rs.getString("title"),
                        rs.getString("stream_name"), rs.getString("push_url"), rs.getString("play_url"),
                        null, rs.getString("cover_url"), rs.getString("status"), null,
                        toLocalDateTime(rs.getTimestamp("create_time")), null),
                roomId);
        return rooms.isEmpty() ? null : rooms.get(0);
    }

    public LiveRoomView findRoomByUserId(Long userId) {
        List<LiveRoomView> rooms = jdbcTemplate.query(
                "SELECT id, user_id, category_id, title, stream_name, push_url, play_url, cover_url, status, create_time " +
                        "FROM live_room WHERE user_id = ? ORDER BY id ASC LIMIT 1",
                (rs, rowNum) -> new LiveRoomView(rs.getLong("id"), rs.getLong("user_id"),
                        (Long) rs.getObject("category_id"), rs.getString("title"), rs.getString("stream_name"),
                        rs.getString("push_url"), rs.getString("play_url"), null, rs.getString("cover_url"),
                        rs.getString("status"), null, toLocalDateTime(rs.getTimestamp("create_time")), null), userId);
        return rooms.isEmpty() ? null : rooms.get(0);
    }

    public boolean updateRoom(Long roomId, Long categoryId, String title, String streamName, String pushUrl, String playUrl, String coverUrl) {
        return jdbcTemplate.update("UPDATE live_room SET category_id=?, title=?, stream_name=?, push_url=?, play_url=?, cover_url=?, status='online', create_time=CURRENT_TIMESTAMP WHERE id=?",
                categoryId, title, streamName, pushUrl, playUrl, coverUrl, roomId) > 0;
    }

    public void closeOtherOnlineRooms(Long userId, Long keepRoomId) {
        jdbcTemplate.update("UPDATE live_room SET status='offline' WHERE user_id=? AND id<>? AND status='online'", userId, keepRoomId);
    }

    public List<LiveRoomView> findRooms(Long categoryId, int limit, int offset) {
        String sql = "SELECT id, user_id, category_id, title, stream_name, push_url, play_url, cover_url, status, create_time "
                + "FROM live_room WHERE status = 'online' "
                + (categoryId == null ? "" : "AND category_id = ? ")
                + "ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?";
        Object[] args = categoryId == null
                ? new Object[]{limit, offset}
                : new Object[]{categoryId, limit, offset};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new LiveRoomView(
                rs.getLong("id"), rs.getLong("user_id"), (Long) rs.getObject("category_id"),
                rs.getString("title"), rs.getString("stream_name"), rs.getString("push_url"),
                rs.getString("play_url"), null, rs.getString("cover_url"), rs.getString("status"), null,
                toLocalDateTime(rs.getTimestamp("create_time")), null), args);
    }

    public long countRooms(Long categoryId) {
        String sql = "SELECT COUNT(*) FROM live_room WHERE status = 'online'"
                + (categoryId == null ? "" : " AND category_id = ?");
        Long count = categoryId == null
                ? jdbcTemplate.queryForObject(sql, Long.class)
                : jdbcTemplate.queryForObject(sql, Long.class, categoryId);
        return count == null ? 0 : count;
    }

    public boolean closeRoom(Long roomId, Long userId) {
        return jdbcTemplate.update(
                "UPDATE live_room SET status = 'offline' WHERE id = ? AND user_id = ? AND status = 'online'",
                roomId, userId) > 0;
    }

    public void resetInteraction(Long roomId) {
        jdbcTemplate.update("DELETE FROM live_danmu WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_likes WHERE room_id = ?", roomId);
    }

    public LiveDanmuView insertDanmu(Long roomId, Long userId, String username, String content, String color) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO live_danmu (room_id, user_id, username, content, color) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"id"});
            statement.setLong(1, roomId);
            if (userId == null) statement.setObject(2, null);
            else statement.setLong(2, userId);
            statement.setString(3, username);
            statement.setString(4, content);
            statement.setString(5, color);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("保存弹幕后未返回弹幕 ID");
        return new LiveDanmuView(key.longValue(), roomId, userId, username, content, color, LocalDateTime.now());
    }

    public List<LiveDanmuView> findDanmus(Long roomId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, room_id, user_id, username, content, color, send_time FROM live_danmu "
                        + "WHERE room_id = ? ORDER BY send_time DESC, id DESC LIMIT ?",
                (rs, rowNum) -> new LiveDanmuView(
                        rs.getLong("id"), rs.getLong("room_id"), (Long) rs.getObject("user_id"),
                        rs.getString("username"), rs.getString("content"), rs.getString("color"),
                        toLocalDateTime(rs.getTimestamp("send_time"))), roomId, limit);
    }

    public long addLike(Long roomId) {
        jdbcTemplate.update("INSERT INTO room_likes (room_id, like_count) VALUES (?, 1) "
                + "ON DUPLICATE KEY UPDATE like_count = like_count + 1", roomId);
        return getLikeCount(roomId);
    }

    public long getLikeCount(Long roomId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT like_count FROM room_likes WHERE room_id = ?", Long.class, roomId);
            return count == null ? 0 : count;
        } catch (EmptyResultDataAccessException ignored) {
            return 0;
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
