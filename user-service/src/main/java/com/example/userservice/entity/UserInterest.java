package com.example.userservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_interest")
public class UserInterest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tag;
    private Integer score;
}
