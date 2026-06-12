package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Danmu;
import com.example.demo.mapper.DanmuMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class DanmuService {
    private final DanmuMapper danmuMapper;

    public DanmuService(DanmuMapper danmuMapper) {
        this.danmuMapper = danmuMapper;
    }

    public void saveDanmu(Danmu danmu) {
        danmuMapper.insert(danmu);
    }

    public List<Danmu> getRecentDanmu(String roomId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<Danmu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Danmu::getRoomId, roomId)
                .orderByDesc(Danmu::getSendTime)
                .last("LIMIT " + safeLimit);
        List<Danmu> list = danmuMapper.selectList(wrapper);
        Collections.reverse(list);
        return list;
    }
}
