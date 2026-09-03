package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("room_likes")
public class RoomLikes {

    @TableId("room_id")
    private Long roomId;

    @TableField("like_count")
    private Long likeCount;
}
