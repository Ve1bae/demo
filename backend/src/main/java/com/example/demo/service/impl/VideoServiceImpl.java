package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.UserVideo;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserVideoMapper;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    private final UserVideoMapper userVideoMapper;

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
            // 设置 sources 字段（用于前端视频播放）
            setVideoSources(video);
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
            // 设置 sources 字段（用于前端视频播放）
            setVideoSources(video);
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
            // 设置 sources 字段（用于前端视频播放）
            setVideoSources(video);
        }
        return video;
    }

    /**
     * 设置视频源信息（用于前端播放）
     */
    private void setVideoSources(Video video) {
        // 如果有 play_url，使用它作为所有清晰度的视频源
        if (video.getPlayUrl() != null && !video.getPlayUrl().isEmpty()) {
            Map<String, String> sources = new HashMap<>();
            sources.put("360P", video.getPlayUrl());
            sources.put("480P", video.getPlayUrl());
            sources.put("720P", video.getPlayUrl());
            sources.put("1080P", video.getPlayUrl());
            video.setSources(sources);
            video.setDefaultQuality("1080P");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> toggleLike(Long userId, Long videoId) {
        Map<String, Object> result = new HashMap<>();
        
        // 获取或创建用户视频关系记录
        UserVideo userVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
        if (userVideo == null) {
            userVideo = new UserVideo();
            userVideo.setUserId(userId);
            userVideo.setVideoId(videoId);
            userVideo.setLiked(false);
            userVideo.setFavorited(false);
            userVideoMapper.insert(userVideo);
        }
        
        // 切换点赞状态
        boolean newLiked = !Boolean.TRUE.equals(userVideo.getLiked());
        userVideo.setLiked(newLiked);
        userVideoMapper.updateById(userVideo);
        
        // 更新视频点赞数
        int delta = newLiked ? 1 : -1;
        userVideoMapper.updateVideoLikeCount(videoId, delta);
        
        // 获取更新后的点赞数
        Video video = baseMapper.selectById(videoId);
        
        result.put("videoId", videoId);
        result.put("liked", newLiked);
        result.put("likeCount", video != null ? video.getLikeCount() : 0);
        
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> toggleFavorite(Long userId, Long videoId) {
        Map<String, Object> result = new HashMap<>();
        
        // 获取或创建用户视频关系记录
        UserVideo userVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
        if (userVideo == null) {
            userVideo = new UserVideo();
            userVideo.setUserId(userId);
            userVideo.setVideoId(videoId);
            userVideo.setLiked(false);
            userVideo.setFavorited(false);
            userVideoMapper.insert(userVideo);
        }
        
        // 切换收藏状态
        boolean newFavorited = !Boolean.TRUE.equals(userVideo.getFavorited());
        userVideo.setFavorited(newFavorited);
        userVideoMapper.updateById(userVideo);
        
        // 更新视频收藏数
        int delta = newFavorited ? 1 : -1;
        userVideoMapper.updateVideoFavoriteCount(videoId, delta);
        
        // 获取更新后的收藏数
        Video video = baseMapper.selectById(videoId);
        
        result.put("videoId", videoId);
        result.put("favorited", newFavorited);
        result.put("favoriteCount", video != null ? video.getFavoriteCount() : 0);
        
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> incrementPlayCount(Long videoId) {
        Map<String, Object> result = new HashMap<>();
        
        // 增加播放量
        userVideoMapper.incrementPlayCount(videoId);
        
        // 获取更新后的播放量
        Video video = baseMapper.selectById(videoId);
        
        result.put("videoId", videoId);
        result.put("playCount", video != null ? video.getPlayCount() : 0);
        
        return result;
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

    @Override
    public Map<String, Object> getUserVideoStatus(Long userId, Long videoId) {
        Map<String, Object> result = new HashMap<>();
        
        // 查询用户视频关系记录
        UserVideo userVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
        
        if (userVideo != null) {
            result.put("liked", Boolean.TRUE.equals(userVideo.getLiked()));
            result.put("favorited", Boolean.TRUE.equals(userVideo.getFavorited()));
        } else {
            result.put("liked", false);
            result.put("favorited", false);
        }
        
        result.put("videoId", videoId);
        return result;
    }
}