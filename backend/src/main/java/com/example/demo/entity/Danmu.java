package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("danmu")
public class Danmu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private Long userId;
    private String username;
    private String content;
    private String color;
    @TableField("send_time")
    private LocalDateTime sendTime;
}
