package com.example.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateDynamicRequest(
        @NotBlank(message = "动态内容不能为空")
        @Size(max = 1000, message = "动态内容不能超过1000字")
        String content,

        @Size(max = 20, message = "一条动态最多提及20位用户")
        List<Long> mentionedUserIds
) {
}
