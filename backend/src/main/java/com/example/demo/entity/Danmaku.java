package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("danmaku")
public class Danmaku {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("video_url")
    private String videoUrl;

    private String content;

    private String color;

    private Double time;

    @TableField("user_id")
    private String userId;

    @TableField("is_user")
    private Boolean isUser;

    @TableField("created_at")
    private LocalDateTime createdAt;
}