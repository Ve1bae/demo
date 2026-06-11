package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.PagedResult;
import com.example.demo.entity.Video;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.MinioStorageService;
import com.example.demo.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Autowired
    private MinioStorageService minioStorageService;

    @Override
    public Video initUpload(Long userId, String title, String description, Long categoryId,
                            String fileName, long fileSize, String contentType) {
        String objectName = "videos/" + userId + "/" + System.currentTimeMillis() + "-" + fileName;

        Video video = new Video();
        video.setUserId(userId);
        video.setTitle(title);
        video.setDescription(description);
        video.setCategoryId(categoryId);
        video.setPlayUrl(objectName);
        video.setStatus("uploading");
        video.setCreatedAt(LocalDateTime.now());
        video.setUpdatedAt(LocalDateTime.now());
        video.setPlayCount(0L);
        video.setLikeCount(0L);
        video.setFavoriteCount(0L);
        video.setCommentCount(0L);
        video.setDuration(0);

        this.save(video);
        return video;
    }

    @Override
    public void completeUpload(Long videoId) {
        Video video = this.getById(videoId);
        if (video == null) return;
        video.setStatus("published");
        video.setUpdatedAt(LocalDateTime.now());
        this.updateById(video);
    }

    @Override
    public PagedResult<Video> getMyVideos(Long userId, int page, int pageSize) {
        QueryWrapper<Video> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("created_at");

        // 手动分页：先查总数，再查当前页
        long total = this.count(qw);

        int offset = (page - 1) * pageSize;
        qw.last("LIMIT " + offset + ", " + pageSize);

        List<Video> list = this.list(qw);
        return new PagedResult<>(list, total, page, pageSize);
    }

    @Override
    public void deleteVideo(Long userId, Long videoId) {
        Video video = this.getById(videoId);
        if (video == null) return;
        if (!video.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人视频");
        }

        try {
            minioStorageService.delete(video.getPlayUrl());
            if (video.getCoverUrl() != null && !video.getCoverUrl().isEmpty()) {
                minioStorageService.delete(video.getCoverUrl());
            }
        } catch (Exception ignored) {}

        this.removeById(videoId);
    }
}
