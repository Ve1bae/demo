package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Video;
import java.util.List;

public interface VideoService extends IService<Video> {
    List<Video> getAllVideos();

    Video getVideoById(Long id);

    Video getVideoByVideoUrl(String videoUrl);
}
