package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.UserVideo;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserVideoMapper;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
        videos.forEach(this::decorateVideo);
        return videos;
    }

    @Override
    public Video getVideoById(Long id) {
        Video video = baseMapper.selectById(id);
        if (video != null) {
            decorateVideo(video);
        }
        return video;
    }

    @Override
    public Video getVideoByVideoUrl(String videoUrl) {
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_url", videoUrl);
        Video video = baseMapper.selectOne(queryWrapper);
        if (video != null) {
            decorateVideo(video);
        }
        return video;
    }

    private void decorateVideo(Video video) {
        video.setVideoId(video.getId());
        if (video.getPlayCount() != null) {
            video.setViews(formatPlayCount(video.getPlayCount()));
        }
        setVideoSources(video);
    }

    private void setVideoSources(Video video) {
        Map<String, String> sources = new HashMap<>();
        if (video.getUrl240p() != null && !video.getUrl240p().isBlank()) {
            sources.put("240P", video.getUrl240p());
        }
        if (video.getUrl360p() != null && !video.getUrl360p().isBlank()) {
            sources.put("360P", video.getUrl360p());
        }
        if (video.getUrl480p() != null && !video.getUrl480p().isBlank()) {
            sources.put("480P", video.getUrl480p());
        }
        if (video.getUrl720p() != null && !video.getUrl720p().isBlank()) {
            sources.put("720P", video.getUrl720p());
        }
        if (video.getUrl1080p() != null && !video.getUrl1080p().isBlank()) {
            sources.put("1080P", video.getUrl1080p());
        }
        if (sources.isEmpty() && video.getPlayUrl() != null && !video.getPlayUrl().isBlank()) {
            sources.put("720P", video.getPlayUrl());
        }
        video.setSources(sources);
        video.setDefaultQuality(video.getDefaultQuality() != null
                ? video.getDefaultQuality()
                : (sources.containsKey("720P") ? "720P" : sources.keySet().stream().findFirst().orElse("720P")));
    }

    @Override
    @Transactional
    public Map<String, Object> toggleLike(Long userId, Long videoId) {
        Map<String, Object> result = new HashMap<>();
        UserVideo userVideo = getOrCreateUserVideo(userId, videoId);
        boolean newLiked = !Boolean.TRUE.equals(userVideo.getLiked());
        userVideo.setLiked(newLiked);
        userVideoMapper.updateById(userVideo);
        userVideoMapper.updateVideoLikeCount(videoId, newLiked ? 1 : -1);

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
        UserVideo userVideo = getOrCreateUserVideo(userId, videoId);
        boolean newFavorited = !Boolean.TRUE.equals(userVideo.getFavorited());
        userVideo.setFavorited(newFavorited);
        userVideoMapper.updateById(userVideo);
        userVideoMapper.updateVideoFavoriteCount(videoId, newFavorited ? 1 : -1);

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
        userVideoMapper.incrementPlayCount(videoId);
        Video video = baseMapper.selectById(videoId);
        result.put("videoId", videoId);
        result.put("playCount", video != null ? video.getPlayCount() : 0);
        return result;
    }

    @Override
    public Map<String, Object> getUserVideoStatus(Long userId, Long videoId) {
        Map<String, Object> result = new HashMap<>();
        UserVideo userVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
        result.put("liked", userVideo != null && Boolean.TRUE.equals(userVideo.getLiked()));
        result.put("favorited", userVideo != null && Boolean.TRUE.equals(userVideo.getFavorited()));
        result.put("videoId", videoId);
        return result;
    }

    private UserVideo getOrCreateUserVideo(Long userId, Long videoId) {
        UserVideo userVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
        if (userVideo != null) {
            return userVideo;
        }

        UserVideo newUserVideo = new UserVideo();
        newUserVideo.setUserId(userId);
        newUserVideo.setVideoId(videoId);
        newUserVideo.setLiked(false);
        newUserVideo.setFavorited(false);

        try {
            userVideoMapper.insert(newUserVideo);
            return newUserVideo;
        } catch (DuplicateKeyException ignored) {
            UserVideo existingUserVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
            if (existingUserVideo != null) {
                return existingUserVideo;
            }
            throw ignored;
        }
    }

    private String formatPlayCount(Integer playCount) {
        if (playCount >= 10000) {
            return String.format("%.1f万", playCount / 10000.0);
        }
        return String.valueOf(playCount);
    }
}
