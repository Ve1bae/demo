package com.example.video.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class VideoVO {
    private Long id;
    private Long videoId;
    private String title;
    private String description;
    private String coverUrl;
    private String playUrl;
    private String videoUrl;
    private String author;
    private Long userId;
    private Integer categoryId;
    private String tags;
    private List<String> tagList;
    private Integer duration;
    private String status;
    private Integer playCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private String defaultQuality;
    private String views;
    private Map<String, String> sources;
    private AuthorInfo authorInfo;
    private Boolean liked;
    private Boolean favorited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getPlayUrl() { return playUrl; }
    public void setPlayUrl(String playUrl) { this.playUrl = playUrl; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public List<String> getTagList() { return tagList; }
    public void setTagList(List<String> tagList) { this.tagList = tagList; }
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
    public String getDefaultQuality() { return defaultQuality; }
    public void setDefaultQuality(String defaultQuality) { this.defaultQuality = defaultQuality; }
    public String getViews() { return views; }
    public void setViews(String views) { this.views = views; }
    public Map<String, String> getSources() { return sources; }
    public void setSources(Map<String, String> sources) { this.sources = sources; }
    public AuthorInfo getAuthorInfo() { return authorInfo; }
    public void setAuthorInfo(AuthorInfo authorInfo) { this.authorInfo = authorInfo; }
    public Boolean getLiked() { return liked; }
    public void setLiked(Boolean liked) { this.liked = liked; }
    public Boolean getFavorited() { return favorited; }
    public void setFavorited(Boolean favorited) { this.favorited = favorited; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** 作者扩展信息：供前端展示 userId 与当前用户是否关注该作者 */
    public static class AuthorInfo {
        private Long userId;
        private Boolean following;
        private String nickname;
        private String avatarUrl;

        public AuthorInfo() { }

        public AuthorInfo(Long userId, Boolean following) {
            this.userId = userId;
            this.following = following;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Boolean getFollowing() { return following; }
        public void setFollowing(Boolean following) { this.following = following; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }
}
