package com.example.demo.dto;

import lombok.Data;

@Data
public class LiveRoomCreateDTO {
    private String title;
    private Long categoryId;
    private String coverUrl;
    private Long userId;
}
