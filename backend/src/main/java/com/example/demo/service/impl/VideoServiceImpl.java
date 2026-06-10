package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Video;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Override
    public List<Video> getAllVideos() {
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_at");
        List<Video> videos = baseMapper.selectList(queryWrapper);
        // 设置 videoId 字段（用于前端兼容）
        videos.forEach(video -> {
            video.setVideoId(video.getId());
            // 设置 views 字段（用于前端兼容）
            if (video.getPlayCount() != null) {
                video.setViews(formatPlayCount(video.getPlayCount()));
            }
        });
        return videos;
    }

    @Override
    public Video getVideoById(Long id) {
        Video video = baseMapper.selectById(id);
        if (video != null) {
            video.setVideoId(video.getId());
            if (video.getPlayCount() != null) {
                video.setViews(formatPlayCount(video.getPlayCount()));
            }
        }
        return video;
    }

    @Override
    public Video getVideoByVideoUrl(String videoUrl) {
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_url", videoUrl);
        Video video = baseMapper.selectOne(queryWrapper);
        if (video != null) {
            video.setVideoId(video.getId());
            if (video.getPlayCount() != null) {
                video.setViews(formatPlayCount(video.getPlayCount()));
            }
        }
        return video;
    }

    /**
     * 格式化播放量显示
     */
    private String formatPlayCount(Integer playCount) {
        if (playCount >= 10000) {
            return String.format("%.1f万", playCount / 10000.0);
        }
        return String.valueOf(playCount);
    }
}