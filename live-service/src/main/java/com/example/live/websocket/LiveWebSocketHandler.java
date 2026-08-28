package com.example.live.websocket;

import com.example.live.model.LiveDanmuView;
import com.example.live.service.LiveService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveWebSocketHandler extends TextWebSocketHandler {
    private final LiveService liveService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<Long, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public LiveWebSocketHandler(LiveService liveService) {
        this.liveService = liveService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long roomId = roomId(session);
        if (roomId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        try {
            liveService.getRoom(roomId);
        } catch (IllegalArgumentException exception) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason(exception.getMessage()));
            return;
        }
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024);
        roomSessions.computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>()).put(session.getId(), safeSession);
        send(safeSession, Map.of("type", "like", "likeCount", liveService.getLikeCount(roomId)));
        broadcast(roomId, Map.of("type", "online_count", "count", onlineCount(roomId)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long roomId = roomId(session);
        if (roomId == null) return;
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
            String type = String.valueOf(payload.getOrDefault("type", "danmu"));
            Long userId = longValue(payload.get("userId"));
            if ("like".equals(type)) {
                long likeCount = liveService.addLike(roomId, userId);
                broadcast(roomId, Map.of("type", "like", "likeCount", likeCount));
                return;
            }
            if (!"danmu".equals(type)) throw new IllegalArgumentException("不支持的直播互动类型");
            LiveDanmuView danmu = liveService.addDanmu(
                    roomId, userId, stringValue(payload.get("username")),
                    stringValue(payload.get("content")), stringValue(payload.get("color")));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "danmu");
            response.put("id", danmu.id());
            response.put("roomId", danmu.roomId());
            response.put("userId", danmu.userId());
            response.put("username", danmu.username());
            response.put("content", danmu.content());
            response.put("color", danmu.color());
            response.put("sendTime", danmu.sendTime());
            broadcast(roomId, response);
        } catch (Exception exception) {
            send(session, Map.of("type", "error", "message",
                    exception.getMessage() == null ? "直播互动消息不合法" : exception.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = roomId(session);
        if (roomId == null) return;
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) roomSessions.remove(roomId);
        }
        broadcast(roomId, Map.of("type", "online_count", "count", onlineCount(roomId)));
    }

    private void broadcast(Long roomId, Map<String, Object> payload) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;
        sessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());
        sessions.values().forEach(session -> send(session, payload));
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception ignored) {
            // Closing or slow sessions are removed on the next broadcast.
        }
    }

    private int onlineCount(Long roomId) {
        return roomSessions.getOrDefault(roomId, Map.of()).size();
    }

    private Long roomId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String path = uri.getPath();
        try {
            return Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
