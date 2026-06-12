package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName("video")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(exist = false)
    private Long videoId;

    private String title;

    @TableField("description")
    private String description;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("play_url")
    private String playUrl;

    private String author;

    @TableField("play_count")
    private Integer playCount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("favorite_count")
    private Integer favoriteCount;

    @TableField("comment_count")
    private Integer commentCount;

    @TableField("video_url")
    private String videoUrl;

    @TableField("url_240p")
    private String url240p;

    @TableField("url_360p")
    private String url360p;

    @TableField("url_480p")
    private String url480p;

    @TableField("url_720p")
    private String url720p;

    @TableField("url_1080p")
    private String url1080p;

    @TableField("default_quality")
    private String defaultQuality;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("duration")
    private String duration;

    @TableField(exist = false)
    private Boolean liked;

    @TableField(exist = false)
    private Boolean favorited;

    @TableField(exist = false)
    private Category category;

    @TableField(exist = false)
    private Author authorInfo;

    @TableField(exist = false)
    private Integer categoryId;

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String views;

    @TableField(exist = false)
    private Map<String, String> sources;

    @TableField(exist = false)
    private List<Tag> tags;

    @Data
    public static class Category {
        private Long categoryId;
        private String categoryName;
    }

    @Data
    public static class Author {
        private Long userId;
        private String nickname;
        private String avatarUrl;
    }
}
