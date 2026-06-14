package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.LiveDanmu;
import com.example.demo.mapper.LiveDanmuMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class LiveDanmuService extends ServiceImpl<LiveDanmuMapper, LiveDanmu> {

    public LiveDanmu saveDanmu(Long roomId, Long userId, String username, String content, String color) {
        LiveDanmu danmu = new LiveDanmu();
        danmu.setRoomId(roomId);
        danmu.setUserId(userId);
        danmu.setUsername(username == null || username.isBlank() ? "游客" : username);
        danmu.setContent(content);
        danmu.setColor(color == null || color.isBlank() ? "#ffffff" : color);
        danmu.setSendTime(LocalDateTime.now());
        save(danmu);
        return danmu;
    }

    public List<LiveDanmu> getRecentDanmu(Long roomId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        QueryWrapper<LiveDanmu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("room_id", roomId)
                .orderByDesc("send_time")
                .last("LIMIT " + safeLimit);
        List<LiveDanmu> list = list(queryWrapper);
        Collections.reverse(list);
        return list;
    }

    public void clearRoomDanmu(Long roomId) {
        if (roomId == null) {
            return;
        }
        QueryWrapper<LiveDanmu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("room_id", roomId);
        remove(queryWrapper);
    }}
