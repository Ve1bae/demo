package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.common.PageResult;
import com.example.demo.dto.LiveRoomCreateDTO;
import com.example.demo.entity.LiveRoom;
import com.example.demo.entity.User;
import com.example.demo.mapper.LiveRoomMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.LiveDanmuService;
import com.example.demo.service.LiveRoomService;
import com.example.demo.service.RoomLikesService;
import com.example.demo.vo.LiveRoomVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LiveRoomServiceImpl extends ServiceImpl<LiveRoomMapper, LiveRoom> implements LiveRoomService {
    private static final String STATUS_ONLINE = "online";
    private static final String STATUS_OFFLINE = "offline";

    @Value("${live.srs.rtmp-base-url:rtmp://localhost/live}")
    private String rtmpBaseUrl;

    @Value("${live.srs.http-base-url:http://localhost:8081}")
    private String httpBaseUrl;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LiveDanmuService liveDanmuService;

    @Autowired
    private RoomLikesService roomLikesService;

    @Override
    public PageResult<LiveRoomVO> listRooms(Long page, Long pageSize, Long categoryId) {
        long current = normalizePositive(page, 1L);
        long size = normalizePositive(pageSize, 12L);

        Page<LiveRoom> pageInfo = new Page<>(current, size);
        LambdaQueryWrapper<LiveRoom> wrapper = new LambdaQueryWrapper<LiveRoom>()
                .orderByDesc(LiveRoom::getCreateTime);
        if (categoryId != null && categoryId > 0) {
            wrapper.eq(LiveRoom::getCategoryId, categoryId);
        }

        Page<LiveRoom> result = this.page(pageInfo, wrapper);
        List<LiveRoomVO> rooms = result.getRecords().stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(rooms, result.getTotal(), current, size);
    }

    @Override
    public LiveRoomVO createRoom(LiveRoomCreateDTO dto, Long headerUserId) {
        if (dto == null || !StringUtils.hasText(dto.getTitle())) {
            throw new IllegalArgumentException("直播间标题不能为空");
        }

        Long userId = resolveUserId(dto, headerUserId);
        LiveRoom room = findUserRoom(userId);
        boolean isNewRoom = room == null;
        if (isNewRoom) {
            room = new LiveRoom();
            room.setUserId(userId);
            room.setStreamName(buildStreamName());
        } else {
            closeOtherUserRooms(userId, room.getId());
            if (!StringUtils.hasText(room.getStreamName())) {
                room.setStreamName(buildStreamName());
            }
        }

        room.setUserId(userId);
        room.setCategoryId(dto.getCategoryId());
        room.setTitle(dto.getTitle());
        room.setPushUrl(joinUrl(rtmpBaseUrl, room.getStreamName()));
        room.setPlayUrl(joinUrl(httpBaseUrl, "live/" + room.getStreamName() + ".flv"));
        room.setCoverUrl(dto.getCoverUrl());
        room.setStatus(STATUS_ONLINE);
        room.setCreateTime(LocalDateTime.now());
        if (isNewRoom) {
            this.save(room);
        } else {
            this.updateById(room);
        }
        resetRoomInteraction(room.getId());
        return toVO(room);
    }

    @Override
    public LiveRoomVO getRoom(Long roomId) {
        LiveRoom room = getExistingRoom(roomId);
        return toVO(room);
    }

    @Override
    public LiveRoomVO closeRoom(Long roomId, Long operatorUserId) {
        LiveRoom room = getExistingRoom(roomId);
        validateRoomOwner(room, operatorUserId);
        room.setStatus(STATUS_OFFLINE);
        this.updateById(room);
        return toVO(room);
    }

    private void resetRoomInteraction(Long roomId) {
        liveDanmuService.clearRoomDanmu(roomId);
        roomLikesService.resetLikeCount(roomId);
    }

    private LiveRoom getExistingRoom(Long roomId) {
        if (roomId == null || roomId <= 0) {
            throw new IllegalArgumentException("直播间 ID 不合法");
        }
        LiveRoom room = this.getById(roomId);
        if (room == null) {
            throw new IllegalArgumentException("直播间不存在");
        }
        return room;
    }

    private Long resolveUserId(LiveRoomCreateDTO dto, Long headerUserId) {
        if (headerUserId != null && headerUserId > 0) {
            return headerUserId;
        }
        throw new IllegalArgumentException("请先登录后再开始直播");
    }

    private void validateRoomOwner(LiveRoom room, Long operatorUserId) {
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new IllegalArgumentException("请先登录后再关闭直播");
        }
        if (room.getUserId() == null || !room.getUserId().equals(operatorUserId)) {
            throw new IllegalArgumentException("只能关闭自己的直播间");
        }
    }

    private LiveRoom findUserRoom(Long userId) {
        return this.list(new LambdaQueryWrapper<LiveRoom>()
                        .eq(LiveRoom::getUserId, userId)
                        .orderByAsc(LiveRoom::getId))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void closeOtherUserRooms(Long userId, Long activeRoomId) {
        List<LiveRoom> rooms = this.list(new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getUserId, userId)
                .ne(LiveRoom::getId, activeRoomId)
                .eq(LiveRoom::getStatus, STATUS_ONLINE));

        rooms.forEach(room -> room.setStatus(STATUS_OFFLINE));
        if (!rooms.isEmpty()) {
            this.updateBatchById(rooms);
        }
    }

    private String buildStreamName() {
        return "room_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return normalizedBase + "/" + normalizedPath;
    }

    private long normalizePositive(Long value, Long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private LiveRoomVO toVO(LiveRoom room) {
        LiveRoomVO vo = LiveRoomVO.fromEntity(room);
        User user = room.getUserId() == null ? null : userMapper.selectById(room.getUserId());
        if (user != null && StringUtils.hasText(user.getNickname())) {
            vo.setAnchorNickname(user.getNickname());
        } else if (user != null && StringUtils.hasText(user.getUsername())) {
            vo.setAnchorNickname(user.getUsername());
        }
        return vo;
    }
}
