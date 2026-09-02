package com.example.video.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.video.entity.Video;
import com.example.video.vo.VideoVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface VideoService extends IService<Video> {
    VideoVO upload(MultipartFile file, String title, String description, String coverUrl, String tags, String author, Long userId, Integer duration);
    List<VideoVO> recommend(int page, int pageSize, Long categoryId, String keyword, Long viewerId);
    VideoVO getByIdWithState(Long videoId, Long viewerId);
    List<VideoVO> getByUserId(Long userId, Long viewerId);
    List<VideoVO> getFavoritesByUserId(Long userId, Long viewerId);
    boolean softDelete(Long videoId, Long userId);
    boolean setVisibility(Long videoId, Long userId, boolean visible);
    Map<String, Object> play(Long videoId, Long userId);
    Map<String, Object> toggleLike(Long userId, Long videoId);
    Map<String, Object> toggleFavorite(Long userId, Long videoId);
    Map<String, Object> status(Long userId, Long videoId);
    List<Map<String, Object>> history(Long userId);
}
