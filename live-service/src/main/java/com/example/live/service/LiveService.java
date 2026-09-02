package com.example.live.service;

import com.example.live.common.PageResult;
import com.example.live.dto.CreateLiveRoomRequest;
import com.example.live.model.LiveDanmuView;
import com.example.live.model.LiveRoomView;
import com.example.live.repository.LiveRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveService {
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DANMU_LENGTH = 255;

    private final LiveRepository repository;
    private final SrsHealthService srsHealthService;
    private final LiveTranscodeService transcodeService;
    private final RestClient userClient = RestClient.create();
    @Value("${live.user-service-base-url:http://user-service:8081}")
    private String userServiceBaseUrl;

    @Value("${live.srs.rtmp-base-url:rtmp://localhost:1935/live}")
    private String rtmpBaseUrl = "rtmp://localhost:1935/live";

    @Value("${live.srs.internal-rtmp-base-url:rtmp://srs:1935/live}")
    private String internalRtmpBaseUrl = "rtmp://srs:1935/live";

    @Value("${live.srs.http-base-url:http://localhost:8081}")
    private String httpBaseUrl = "http://localhost:8081";

    public LiveService(LiveRepository repository, SrsHealthService srsHealthService, LiveTranscodeService transcodeService) {
        this.repository = repository;
        this.srsHealthService = srsHealthService;
        this.transcodeService = transcodeService;
    }

    @Transactional
    public LiveRoomView createRoom(Long userId, CreateLiveRoomRequest request) {
        requirePositive(userId, "用户 ID");
        if (request == null || !StringUtils.hasText(request.title())) {
            throw new IllegalArgumentException("直播间标题不能为空");
        }
        LiveRoomView existing = repository.findRoomByUserId(userId);
        String streamName = existing != null && StringUtils.hasText(existing.streamName())
                ? existing.streamName()
                : "room_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String pushUrl = joinUrl(rtmpBaseUrl, streamName);
        String pullUrl = joinUrl(httpBaseUrl, "live/" + streamName + ".flv");
        Long roomId;
        if (existing == null) {
            roomId = repository.insertRoom(userId, request.categoryId(), request.title().trim(), streamName,
                    pushUrl, pullUrl, normalizeCover(request.coverUrl()));
        } else {
            repository.updateRoom(existing.roomId(), request.categoryId(), request.title().trim(), streamName, pushUrl, pullUrl, normalizeCover(request.coverUrl()));
            roomId = existing.roomId();
        }
        repository.resetInteraction(roomId);
        repository.closeOtherOnlineRooms(userId, roomId);
        LiveRoomView room = enrich(repository.findRoom(roomId));
        transcodeService.start(roomId, joinUrl(internalRtmpBaseUrl, streamName), joinUrl(internalRtmpBaseUrl, streamName + "_480p"), joinUrl(internalRtmpBaseUrl, streamName + "_720p"));
        return room;
    }

    public PageResult<LiveRoomView> listRooms(int page, int pageSize, Long categoryId) {
        long safePage = page <= 0 ? 1 : page;
        int safeSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (int) ((safePage - 1) * safeSize);
        List<LiveRoomView> rooms = repository.findRooms(categoryId, safeSize, offset).stream().map(room -> {
            if ("online".equals(room.status())) {
                transcodeService.start(room.roomId(), joinUrl(internalRtmpBaseUrl, room.streamName()), joinUrl(internalRtmpBaseUrl, room.streamName() + "_480p"), joinUrl(internalRtmpBaseUrl, room.streamName() + "_720p"));
            }
            return enrich(room);
        }).toList();
        return new PageResult<>(rooms,
                repository.countRooms(categoryId), safePage, safeSize);
    }

    public LiveRoomView getRoom(Long roomId) {
        requirePositive(roomId, "直播间 ID");
        LiveRoomView room = repository.findRoom(roomId);
        if (room == null) throw new IllegalArgumentException("直播间不存在");
        return enrich(room);
    }

    @Transactional
    public LiveRoomView closeRoom(Long roomId, Long userId) {
        requirePositive(roomId, "直播间 ID");
        requirePositive(userId, "用户 ID");
        LiveRoomView room = getRoom(roomId);
        if (!room.userId().equals(userId)) throw new IllegalArgumentException("只能关闭自己的直播间");
        if (!repository.closeRoom(roomId, userId)) throw new IllegalArgumentException("直播间已经结束");
        transcodeService.stop(roomId);
        return getRoom(roomId);
    }

    public List<LiveDanmuView> getDanmus(Long roomId, int limit) {
        ensureRoom(roomId);
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        return repository.findDanmus(roomId, safeLimit).reversed();
    }

    public long getLikeCount(Long roomId) {
        ensureRoom(roomId);
        return repository.getLikeCount(roomId);
    }

    @Transactional
    public long addLike(Long roomId, Long userId) {
        ensureRoomOnline(roomId);
        requirePositive(userId, "用户 ID");
        return repository.addLike(roomId);
    }

    @Transactional
    public LiveDanmuView addDanmu(Long roomId, Long userId, String username, String content, String color) {
        ensureRoomOnline(roomId);
        requirePositive(userId, "用户 ID");
        if (!StringUtils.hasText(content) || content.trim().length() > MAX_DANMU_LENGTH) {
            throw new IllegalArgumentException("弹幕内容为空或超过255字");
        }
        String safeUsername = StringUtils.hasText(username) ? username.trim() : "用户 " + userId;
        String safeColor = StringUtils.hasText(color) ? color.trim() : "#ffffff";
        return repository.insertDanmu(roomId, userId, safeUsername, content.trim(), safeColor);
    }

    public Map<String, Object> srsHealth() {
        return srsHealthService.health();
    }

    private LiveRoomView enrich(LiveRoomView room) {
        if (room == null) return null;
        Map<String, String> qualityUrls = new LinkedHashMap<>();
        qualityUrls.put("原画", room.pullUrl());
        qualityUrls.put("720P", joinUrl(httpBaseUrl, "live/" + room.streamName() + "_720p.flv"));
        qualityUrls.put("480P", joinUrl(httpBaseUrl, "live/" + room.streamName() + "_480p.flv"));
        Boolean active = srsHealthService.probeEnabled()
                ? srsHealthService.isStreamActive(room.streamName()) : null;
        String nickname = room.anchorNickname();
        try {
            Map response = userClient.get().uri(userServiceBaseUrl + "/api/user/internal/" + room.userId()).retrieve().body(Map.class);
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map m && m.get("nickname") != null) nickname = String.valueOf(m.get("nickname"));
        } catch (Exception ignored) { }
        return new LiveRoomView(room.roomId(), room.userId(), room.categoryId(), room.title(), room.streamName(),
                room.pushUrl(), room.pullUrl(), qualityUrls, room.coverUrl(), room.status(), active, room.createdAt(), nickname);
    }

    private void ensureRoom(Long roomId) {
        requirePositive(roomId, "直播间 ID");
        if (repository.findRoom(roomId) == null) throw new IllegalArgumentException("直播间不存在");
    }

    private void ensureRoomOnline(Long roomId) {
        LiveRoomView room = getRoom(roomId);
        if (!"online".equals(room.status())) throw new IllegalArgumentException("直播间未开播或已结束");
    }

    private String normalizeCover(String coverUrl) {
        return StringUtils.hasText(coverUrl) ? coverUrl.trim() : null;
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/" + (path.startsWith("/") ? path.substring(1) : path);
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) throw new IllegalArgumentException(name + "不合法");
    }
}
