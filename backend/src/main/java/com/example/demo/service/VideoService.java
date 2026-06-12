package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Video;
import java.util.List;
import java.util.Map;

public interface VideoService extends IService<Video> {
    List<Video> getAllVideos();

    Video getVideoById(Long id);

    Video getVideoByVideoUrl(String videoUrl);

    List<Video> getVideosByUserId(Long userId);

    List<Video> getFavoriteVideosByUserId(Long userId);

    long countVideosByUserId(Long userId);

    long countFavoriteVideosByUserId(Long userId);

    boolean setVisibility(Long userId, Long videoId, boolean visible);

    boolean deleteOwnVideo(Long userId, Long videoId);

    Map<String, Object> toggleLike(Long userId, Long videoId);

    Map<String, Object> toggleFavorite(Long userId, Long videoId);

    Map<String, Object> incrementPlayCount(Long videoId);

    Map<String, Object> getUserVideoStatus(Long userId, Long videoId);
}
