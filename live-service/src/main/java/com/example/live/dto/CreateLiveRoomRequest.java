package com.example.live.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLiveRoomRequest(
        @NotBlank(message = "直播间标题不能为空")
        @Size(max = 100, message = "直播间标题不能超过100字")
        String title,
        Long categoryId,
        @Size(max = 500, message = "封面地址不能超过500字")
        String coverUrl
) {
}
