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

    @Value("${live.srs.rtmp-base-url:rtmp://localhost:1935/live}")
    private String rtmpBaseUrl = "rtmp://localhost:1935/live";

    @Value("${live.srs.http-base-url:http://localhost:8081}")
    private String httpBaseUrl = "http://localhost:8081";

    public LiveService(LiveRepository repository, SrsHealthService srsHealthService) {
        this.repository = repository;
        this.srsHealthService = srsHealthService;
    }

    @Transactional
    public LiveRoomView createRoom(Long userId, CreateLiveRoomRequest request) {
        requirePositive(userId, "用户 ID");
        if (request == null || !StringUtils.hasText(request.title())) {
            throw new IllegalArgumentException("直播间标题不能为空");
        }
        String streamName = "room_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String pushUrl = joinUrl(rtmpBaseUrl, streamName);
        String pullUrl = joinUrl(httpBaseUrl, "live/" + streamName + ".flv");
        Long roomId = repository.insertRoom(
                userId, request.categoryId(), request.title().trim(), streamName,
                pushUrl, pullUrl, normalizeCover(request.coverUrl()));
        repository.resetInteraction(roomId);
        return enrich(repository.findRoom(roomId));
    }

    public PageResult<LiveRoomView> listRooms(int page, int pageSize, Long categoryId) {
        long safePage = page <= 0 ? 1 : page;
        int safeSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (int) ((safePage - 1) * safeSize);
        return new PageResult<>(repository.findRooms(categoryId, safeSize, offset),
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
        qualityUrls.put("720P", room.pullUrl().replace(".flv", "_720p.flv"));
        qualityUrls.put("480P", room.pullUrl().replace(".flv", "_480p.flv"));
        Boolean active = srsHealthService.probeEnabled()
                ? srsHealthService.isStreamActive(room.streamName()) : null;
        return new LiveRoomView(room.roomId(), room.userId(), room.categoryId(), room.title(), room.streamName(),
                room.pushUrl(), room.pullUrl(), qualityUrls, room.coverUrl(), room.status(), active, room.createdAt());
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
