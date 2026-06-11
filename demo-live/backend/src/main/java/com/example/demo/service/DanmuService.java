package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Danmu;
import com.example.demo.mapper.DanmuMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class DanmuService {

    @Autowired
    private DanmuMapper danmuMapper;

    // 保存弹幕
    public void saveDanmu(Danmu danmu) {
        danmuMapper.insert(danmu);
    }

    // 获取最近的历史弹幕（按时间倒序取 limit 条，然后正序返回）
    public List<Danmu> getRecentDanmu(String roomId, int limit) {
        LambdaQueryWrapper<Danmu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Danmu::getRoomId, roomId)
                .orderByDesc(Danmu::getSendTime)
                .last("limit " + limit);
        List<Danmu> list = danmuMapper.selectList(wrapper);
        Collections.reverse(list); // 变为时间从早到晚
        return list;
    }
}