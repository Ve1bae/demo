package com.example.demo.vo;

import lombok.Data;

@Data
public class LoginUserVO {
    private Long id;          // 核心：用户ID
    private String username;  // 账号
    private String nickname;  // 昵称
}
