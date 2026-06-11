package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.PagedResult;
import com.example.demo.entity.Video;

public interface VideoService extends IService<Video> {

    /** 初始化分片上传并创建视频记录 */
    Video initUpload(Long userId, String title, String description, Long categoryId, String fileName, long fileSize, String contentType);

    /** 完成上传（合并分片后调用），更新状态和 playUrl */
    void completeUpload(Long videoId, String playUrl);

    /** 用户自己的视频列表（分页） */
    PagedResult<Video> getMyVideos(Long userId, int page, int pageSize);

    /** 删除视频 */
    void deleteVideo(Long userId, Long videoId);
}
