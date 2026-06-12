package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_follow")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("follow_user_id")
    private Long followUserId;

    @TableField(value = "follower_id", exist = false)
    private Long followerId;

    @TableField(value = "followee_id", exist = false)
    private Long followeeId;

    @TableField(value = "following_id", exist = false)
    private Long followingId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
