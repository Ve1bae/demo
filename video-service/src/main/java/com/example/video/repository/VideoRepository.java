package com.example.video.repository;

import com.example.video.model.Video;

import java.util.List;
import java.util.Set;

public interface VideoRepository {
    List<Video> findPublicVideos(Integer categoryId);
    Set<Long> findViewedVideoIds(Long userId);
}
