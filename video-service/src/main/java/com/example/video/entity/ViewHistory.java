package com.example.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("view_history")
public class ViewHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    private Integer viewCount;
    private Integer progressSeconds;
    private LocalDateTime lastViewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getProgressSeconds() { return progressSeconds; }
    public void setProgressSeconds(Integer progressSeconds) { this.progressSeconds = progressSeconds; }
    public LocalDateTime getLastViewedAt() { return lastViewedAt; }
    public void setLastViewedAt(LocalDateTime lastViewedAt) { this.lastViewedAt = lastViewedAt; }
}
