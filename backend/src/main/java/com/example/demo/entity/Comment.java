package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("video_id")
    private Long videoId;

    @TableField("user_id")
    private Long userId;

    private String content;

    @TableField("parent_id")
    private Long parentId;

    @TableField(exist = false)
    private Long rootId;

    @TableField(exist = false)
    private Long replyToUserId;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Long commentId;

    @TableField(exist = false)
    private User user;

    @TableField(exist = false)
    private User replyToUser;

    @TableField(exist = false)
    private Boolean liked;

    @TableField(exist = false)
    private List<Comment> replies;

    @TableField(exist = false)
    private Integer replyCount;
}
