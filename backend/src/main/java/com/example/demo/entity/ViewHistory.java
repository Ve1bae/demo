package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("view_history")
public class ViewHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("video_id")
    private Long videoId;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("last_viewed_at")
    private LocalDateTime lastViewedAt;

    @TableField("progress_seconds")
    private Integer progressSeconds;

    @TableField(exist = false)
    private Video video;
}
