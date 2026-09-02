package com.example.video.vo;

import java.time.LocalDateTime;

public class DanmakuVO {
    private Long id;
    private Long videoId;
    private Long userId;
    private String username;
    private String content;
    private String color;
    private Integer timeSeconds;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getTimeSeconds() { return timeSeconds; }
    public void setTimeSeconds(Integer timeSeconds) { this.timeSeconds = timeSeconds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
