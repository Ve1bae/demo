package com.example.userservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("user_follow")
public class UserFollow {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("user_id") private Long userId;
    @TableField("follow_user_id") private Long followUserId;
    @TableField("created_at") private LocalDateTime createdAt;
}
