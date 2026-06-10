package com.example.demo.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DanmuWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(DanmuWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 存储每个房间的会话列表: roomId -> Map<sessionId, WebSocketSession>
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = getRoomId(session);
        logger.info("WebSocket 连接建立：room={}, session={}", roomId, session.getId());
        Map<String, WebSocketSession> sessions = roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        sessions.put(session.getId(), session);

        // 广播当前在线人数
        broadcastOnlineCount(roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String roomId = getRoomId(session);

        String username = "游客";
        String content = payload;

        try {
            Map<String, String> json = objectMapper.readValue(payload, Map.class);
            if (json.containsKey("username")) username = json.get("username");
            if (json.containsKey("content")) content = json.get("content");
        } catch (Exception e) {
            logger.debug("非 JSON 消息，作为纯文本处理: {}", payload);
        }

        if (content == null || content.trim().isEmpty()) {
            logger.warn("忽略空消息");
            return;
        }

        // 构造要广播的弹幕 JSON
        Map<String, Object> broadcastData = new HashMap<>();
        broadcastData.put("type", "danmu");
        broadcastData.put("username", username);
        broadcastData.put("content", content);
        String broadcastMsg = objectMapper.writeValueAsString(broadcastData);

        logger.info("广播弹幕：room={}, msg={}", roomId, broadcastMsg);
        broadcastToRoom(roomId, broadcastMsg, null);
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
        logger.info("WebSocket 连接关闭：room={}, session={}", roomId, session.getId());
        // 广播当前在线人数
        broadcastOnlineCount(roomId);
    }

    private void broadcastToRoom(String roomId, String message, WebSocketSession excludeSession) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) return;

        sessions.values().forEach(session -> {
            if (session.isOpen() && (excludeSession == null || !session.getId().equals(excludeSession.getId()))) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    logger.error("广播失败", e);
                }
            }
        });
    }

    // 新增：广播在线人数
    private void broadcastOnlineCount(String roomId) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        int count = (sessions == null) ? 0 : sessions.size();
        Map<String, Object> countData = new HashMap<>();
        countData.put("type", "online_count");
        countData.put("count", count);
        try {
            String countMsg = objectMapper.writeValueAsString(countData);
            broadcastToRoom(roomId, countMsg, null);
        } catch (Exception e) {
            logger.error("广播在线人数失败", e);
        }
    }

    private String getRoomId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }
}