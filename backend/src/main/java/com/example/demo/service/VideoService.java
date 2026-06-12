package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Video;
import java.util.List;
import java.util.Map;

public interface VideoService extends IService<Video> {
    List<Video> getAllVideos();

    Video getVideoById(Long id);

    Video getVideoByVideoUrl(String videoUrl);

    List<Video> getRecommendedFeed(Long userId, Integer page, Integer pageSize, Integer categoryId, String keyword);

    List<Video> getRecommendedVideos(Long currentVideoId, Long userId, int limit);

    Map<String, Object> toggleLike(Long userId, Long videoId);

    Map<String, Object> toggleFavorite(Long userId, Long videoId);

    Map<String, Object> incrementPlayCount(Long videoId, Long userId);

    Map<String, Object> touchViewHistory(Long videoId, Long userId);

    Map<String, Object> updateViewHistoryProgress(Long videoId, Long userId, Integer progressSeconds);

    Map<String, Object> getUserVideoStatus(Long userId, Long videoId);

    boolean deleteOwnVideo(Long userId, Long videoId);
}
