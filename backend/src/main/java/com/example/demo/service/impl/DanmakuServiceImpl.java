package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Danmaku;
import com.example.demo.mapper.DanmakuMapper;
import com.example.demo.service.DanmakuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DanmakuServiceImpl extends ServiceImpl<DanmakuMapper, Danmaku> implements DanmakuService {

    @Override
    public boolean saveDanmaku(Danmaku danmaku) {
        danmaku.setCreatedAt(LocalDateTime.now());
        return baseMapper.insert(danmaku) > 0;
    }

    @Override
    public List<Danmaku> getDanmakuByVideoUrl(String videoUrl) {
        QueryWrapper<Danmaku> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_url", videoUrl).orderByAsc("time");
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<Danmaku> getDanmakuByUserId(String userId) {
        QueryWrapper<Danmaku> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).orderByDesc("created_at");
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public boolean deleteDanmaku(Long id) {
        return baseMapper.deleteById(id) > 0;
    }
}