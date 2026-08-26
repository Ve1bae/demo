package com.example.demo.websocket;

import com.example.demo.entity.LiveDanmu;
import com.example.demo.service.LiveDanmuService;
import com.example.demo.service.RoomLikesService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveInteractWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;
    private static final int MAX_DANMU_CONTENT_LENGTH = 255;

    private final RoomLikesService roomLikesService;
    private final LiveDanmuService liveDanmuService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<Long, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long roomId = getRoomId(session);
        if (roomId == null) {
            closeQuietly(session);
            return;
        }
        WebSocketSession managedSession = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MILLIS,
                SEND_BUFFER_SIZE_LIMIT
        );
        roomSessions.computeIfAbsent(roomId, key -> new ConcurrentHashMap<>()).put(session.getId(), managedSession);
        broadcastOnlineCount(roomId);
        sendLikeCount(managedSession, roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long roomId = getRoomId(session);
        if (roomId == null) {
            return;
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Ignore invalid live interact message: {}", message.getPayload());
            return;
        }

        String type = String.valueOf(payload.getOrDefault("type", "danmu"));
        if ("like".equals(type)) {
            long likeCount = roomLikesService.addLike(roomId);
            broadcastJson(roomId, Map.of("type", "like", "likeCount", likeCount));
            return;
        }

        String content = asString(payload.get("content"));
        if (content == null || content.isBlank() || content.trim().length() > MAX_DANMU_CONTENT_LENGTH) {
            log.warn("Ignore invalid live danmu, roomId={}, length={}", roomId, content == null ? 0 : content.trim().length());
            return;
        }

        LiveDanmu danmu;
        try {
            danmu = liveDanmuService.saveDanmu(
                    roomId,
                    asLong(payload.get("userId")),
                    asString(payload.getOrDefault("username", "游客")),
                    content.trim(),
                    asString(payload.getOrDefault("color", "#ffffff"))
            );
        } catch (RuntimeException e) {
            log.warn("Ignore live danmu save failure, roomId={}, message={}", roomId, e.getMessage());
            return;
        }
        broadcastJson(roomId, Map.of(
                "type", "danmu",
                "id", danmu.getId(),
                "roomId", roomId,
                "userId", danmu.getUserId() == null ? "" : danmu.getUserId(),
                "username", danmu.getUsername(),
                "content", danmu.getContent(),
                "color", danmu.getColor(),
                "sendTime", danmu.getSendTime().toString()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = getRoomId(session);
        if (roomId == null) {
            return;
        }
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        broadcastOnlineCount(roomId);
    }

    private void broadcastOnlineCount(Long roomId) {
        int count = roomSessions.getOrDefault(roomId, Map.of()).size();
        broadcastJson(roomId, Map.of("type", "online_count", "count", count));
    }

    private void sendLikeCount(WebSocketSession session, Long roomId) {
        sendJson(session, Map.of("type", "like", "likeCount", roomLikesService.getLikeCount(roomId)));
    }

    private void broadcastJson(Long roomId, Map<String, Object> payload) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        sessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());
        if (sessions.isEmpty()) {
            roomSessions.remove(roomId);
            return;
        }
        sessions.values().forEach(session -> sendJson(session, payload));
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            if (!session.isOpen()) {
                return;
            }
            log.warn("Send live interact message failed: {}", e.getMessage());
        }
    }

    private Long getRoomId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex + 1 >= path.length()) {
            return null;
        }
        try {
            return Long.parseLong(path.substring(slashIndex + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.BAD_DATA);
        } catch (IOException ignored) {
        }
    }
}
