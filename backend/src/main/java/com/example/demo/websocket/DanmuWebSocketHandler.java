package com.example.demo.websocket;

import com.example.demo.entity.Danmu;
import com.example.demo.service.DanmuService;
import com.example.demo.service.RoomLikesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DanmuWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(DanmuWebSocketHandler.class);
    private static final Pattern JSON_FIELD_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"((?:\\\\.|[^\"])*)\"|-?\\d+(?:\\.\\d+)?|true|false|null)");
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final RoomLikesService roomLikesService;
    private final DanmuService danmuService;

    public DanmuWebSocketHandler(RoomLikesService roomLikesService, DanmuService danmuService) {
        this.roomLikesService = roomLikesService;
        this.danmuService = danmuService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = getRoomId(session);
        roomSessions.computeIfAbsent(roomId, key -> new ConcurrentHashMap<>()).put(session.getId(), session);
        broadcastOnlineCount(roomId);
        broadcastLikeCount(roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = getRoomId(session);
        Map<String, String> payload;
        try {
            payload = parseFlatJson(message.getPayload());
        } catch (Exception e) {
            log.warn("Ignore invalid live message: {}", message.getPayload());
            return;
        }

        String type = payload.getOrDefault("type", "danmu");
        if ("like".equals(type)) {
            long likeCount = roomLikesService.addLike(roomId);
            broadcastJson(roomId, Map.of("type", "like", "likeCount", likeCount));
            return;
        }

        String content = payload.getOrDefault("content", "").trim();
        if (content.isEmpty()) {
            return;
        }

        String username = payload.getOrDefault("username", "游客");
        String color = payload.getOrDefault("color", "#ffffff");
        Long userId = parseLong(payload.get("userId"));

        Danmu danmu = new Danmu();
        danmu.setRoomId(roomId);
        danmu.setUserId(userId);
        danmu.setUsername(username);
        danmu.setContent(content);
        danmu.setColor(color);
        danmu.setSendTime(LocalDateTime.now());
        danmuService.saveDanmu(danmu);

        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("type", "danmu");
        broadcast.put("username", username);
        broadcast.put("content", content);
        broadcast.put("color", color);
        broadcast.put("sendTime", danmu.getSendTime().toString());
        broadcastJson(roomId, broadcast);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = getRoomId(session);
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        broadcastOnlineCount(roomId);
    }

    private void broadcastOnlineCount(String roomId) {
        broadcastJson(roomId, Map.of("type", "online_count", "count", getOnlineCount(roomId)));
    }

    private void broadcastLikeCount(String roomId) {
        broadcastJson(roomId, Map.of("type", "like", "likeCount", roomLikesService.getLikeCount(roomId)));
    }

    private int getOnlineCount(String roomId) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions == null ? 0 : sessions.size();
    }

    private void broadcastJson(String roomId, Map<String, ?> data) {
        try {
            broadcastToRoom(roomId, toJson(data));
        } catch (Exception e) {
            log.warn("Broadcast live message failed: {}", e.getMessage());
        }
    }

    private void broadcastToRoom(String roomId, String message) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        sessions.values().forEach(session -> {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.warn("Send live message failed: {}", e.getMessage());
            }
        });
    }

    private String getRoomId(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        String[] parts = path.split("/");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> parseFlatJson(String json) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = JSON_FIELD_PATTERN.matcher(json == null ? "" : json);
        while (matcher.find()) {
            String rawValue = matcher.group(3) != null ? unescapeJson(matcher.group(3)) : matcher.group(2);
            if (!"null".equals(rawValue)) {
                values.put(matcher.group(1), rawValue);
            }
        }
        return values;
    }

    private String toJson(Map<String, ?> data) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : data.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        return builder.append('}').toString();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String unescapeJson(String value) {
        return value == null ? "" : value
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\");
    }
}
