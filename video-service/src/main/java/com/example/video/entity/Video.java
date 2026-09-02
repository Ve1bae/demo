package com.example.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;

import java.time.LocalDateTime;

@TableName("video")
public class Video {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private String playUrl;
    private String author;
    private Long userId;
    private Integer categoryId;
    private Integer duration;
    private String status;
    private Integer playCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private String videoUrl;
    @TableField("url_240p") private String url240p;
    @TableField("url_360p") private String url360p;
    @TableField("url_480p") private String url480p;
    @TableField("url_720p") private String url720p;
    @TableField("url_1080p") private String url1080p;
    private String defaultQuality;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getPlayUrl() { return playUrl; }
    public void setPlayUrl(String playUrl) { this.playUrl = playUrl; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPlayCount() { return playCount; }
    public void setPlayCount(Integer playCount) { this.playCount = playCount; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getUrl240p() { return url240p; }
    public void setUrl240p(String url240p) { this.url240p = url240p; }
    public String getUrl360p() { return url360p; }
    public void setUrl360p(String url360p) { this.url360p = url360p; }
    public String getUrl480p() { return url480p; }
    public void setUrl480p(String url480p) { this.url480p = url480p; }
    public String getUrl720p() { return url720p; }
    public void setUrl720p(String url720p) { this.url720p = url720p; }
    public String getUrl1080p() { return url1080p; }
    public void setUrl1080p(String url1080p) { this.url1080p = url1080p; }
    public String getDefaultQuality() { return defaultQuality; }
    public void setDefaultQuality(String defaultQuality) { this.defaultQuality = defaultQuality; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
