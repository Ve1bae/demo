package com.example.demo.dto;

import lombok.Data;

@Data
public class UserProfileUpdateDTO {
    private String nickname;
    private String avatarUrl;
    private String bio;
}
