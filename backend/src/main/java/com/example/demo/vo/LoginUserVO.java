package com.example.demo.vo;

import lombok.Data;

@Data
public class LoginUserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
}
