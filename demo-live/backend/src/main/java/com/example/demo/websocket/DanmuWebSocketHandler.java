package com.example.demo.websocket;

import com.example.demo.entity.Danmu;
import com.example.demo.service.DanmuService;
import com.example.demo.service.RoomLikesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

@Component
public class DanmuWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(DanmuWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 存储每个房间的会话列表: roomId -> Map<sessionId, WebSocketSession>
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    @Autowired
    private RoomLikesService roomLikesService;

    @Autowired
    private DanmuService danmuService;   // 新增：注入弹幕服务

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = getRoomId(session);
        logger.info("WebSocket 连接建立：room={}, session={}", roomId, session.getId());
        roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        broadcastOnlineCount(roomId);
        broadcastLikeCount(roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String roomId = getRoomId(session);

        // 解析 JSON
        Map<String, Object> json;
        try {
            json = objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            logger.warn("非 JSON 消息，忽略: {}", payload);
            return;
        }

        String type = (String) json.getOrDefault("type", "danmu");

        // 处理点赞
        if ("like".equals(type)) {
            long newCount = roomLikesService.addLike(roomId);
            Map<String, Object> likeBroadcast = new HashMap<>();
            likeBroadcast.put("type", "like");
            likeBroadcast.put("likeCount", newCount);
            String broadcastMsg = objectMapper.writeValueAsString(likeBroadcast);
            broadcastToRoom(roomId, broadcastMsg, null);
            logger.info("点赞广播：room={}, count={}", roomId, newCount);
            return;
        }

        // 处理弹幕（type 为 danmu 或未指定）
        String username = (String) json.getOrDefault("username", "游客");
        String content = (String) json.getOrDefault("content", "");
        String color = (String) json.getOrDefault("color", "#ffffff");
        Long userId = json.containsKey("userId") ? ((Number) json.get("userId")).longValue() : null;

        if (content == null || content.trim().isEmpty()) {
            logger.warn("忽略空弹幕内容");
            return;
        }

        // 保存弹幕到数据库（异步或同步均可，这里同步保存）
        try {
            Danmu danmu = new Danmu();
            danmu.setRoomId(roomId);
            danmu.setUserId(userId);
            danmu.setUsername(username);
            danmu.setContent(content);
            danmu.setColor(color);
            danmu.setSendTime(LocalDateTime.now());
            danmuService.saveDanmu(danmu);
            logger.debug("弹幕已保存：room={}, user={}, content={}", roomId, username, content);
        } catch (Exception e) {
            logger.error("保存弹幕失败", e);
            // 保存失败不影响弹幕广播
        }

        // 构造广播弹幕 JSON（包含颜色）
        Map<String, Object> broadcastData = new HashMap<>();
        broadcastData.put("type", "danmu");
        broadcastData.put("username", username);
        broadcastData.put("content", content);
        broadcastData.put("color", color);
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

    private void broadcastLikeCount(String roomId) {
        try {
            long likeCount = roomLikesService.getLikeCount(roomId);
            Map<String, Object> likeData = new HashMap<>();
            likeData.put("type", "like");
            likeData.put("likeCount", likeCount);
            String likeMsg = objectMapper.writeValueAsString(likeData);
            broadcastToRoom(roomId, likeMsg, null);
            logger.info("广播初始点赞数：room={}, count={}", roomId, likeCount);
        } catch (Exception e) {
            logger.error("广播点赞数失败", e);
        }
    }

    private String getRoomId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }
}