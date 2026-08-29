package com.example.video.service;

import com.example.video.model.Video;

import java.util.List;

public interface RecommendationService {
    List<Video> recommend(Long userId, Integer page, Integer pageSize, Integer categoryId, String keyword);
}
