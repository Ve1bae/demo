package com.example.demo.vo;

import lombok.Data;

@Data
public class UserProfileVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private Long followingCount;
    private Long followerCount;
    private Boolean following;
}
