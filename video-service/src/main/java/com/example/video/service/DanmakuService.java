package com.example.video.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.video.entity.Danmaku;
import com.example.video.mapper.DanmakuMapper;
import com.example.video.mapper.VideoMapper;
import com.example.video.vo.DanmakuVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DanmakuService {
    private final DanmakuMapper danmakuMapper;
    private final VideoMapper videoMapper;

    public DanmakuService(DanmakuMapper danmakuMapper, VideoMapper videoMapper) {
        this.danmakuMapper = danmakuMapper;
        this.videoMapper = videoMapper;
    }

    public List<DanmakuVO> list(Long videoId, int limit) {
        if (videoId == null || videoId <= 0) throw new IllegalArgumentException("视频 ID 不合法");
        if (videoMapper.selectById(videoId) == null) throw new IllegalArgumentException("视频不存在");
        int safeLimit = limit <= 0 ? 100 : Math.min(limit, 500);
        return danmakuMapper.selectList(new QueryWrapper<Danmaku>()
                .eq("video_id", videoId).orderByAsc("time_seconds").orderByAsc("id")
                .last("LIMIT " + safeLimit)).stream().map(this::toVO).toList();
    }

    public DanmakuVO add(Long videoId, Long userId, String username, String content, String color, Integer timeSeconds) {
        if (videoId == null || videoId <= 0) throw new IllegalArgumentException("视频 ID 不合法");
        if (userId == null || userId <= 0) throw new IllegalArgumentException("用户 ID 不合法");
        if (content == null || content.isBlank() || content.length() > 255) throw new IllegalArgumentException("弹幕内容为空或超过255字");
        if (videoMapper.selectById(videoId) == null) throw new IllegalArgumentException("视频不存在");
        Danmaku danmaku = new Danmaku();
        danmaku.setVideoId(videoId);
        danmaku.setUserId(userId);
        danmaku.setUsername(username == null || username.isBlank() ? "用户 " + userId : username.trim());
        danmaku.setContent(content.trim());
        danmaku.setColor(color == null || color.isBlank() ? "#ffffff" : color.trim());
        danmaku.setTimeSeconds(timeSeconds == null || timeSeconds < 0 ? 0 : timeSeconds);
        danmaku.setCreatedAt(LocalDateTime.now());
        danmakuMapper.insert(danmaku);
        return toVO(danmaku);
    }

    private DanmakuVO toVO(Danmaku danmaku) {
        DanmakuVO vo = new DanmakuVO();
        vo.setId(danmaku.getId());
        vo.setVideoId(danmaku.getVideoId());
        vo.setUserId(danmaku.getUserId());
        vo.setUsername(danmaku.getUsername());
        vo.setContent(danmaku.getContent());
        vo.setColor(danmaku.getColor());
        vo.setTimeSeconds(danmaku.getTimeSeconds());
        vo.setCreatedAt(danmaku.getCreatedAt());
        return vo;
    }
}
