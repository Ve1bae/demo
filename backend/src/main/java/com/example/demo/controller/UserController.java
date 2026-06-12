package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import com.example.demo.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private VideoService videoService;

    @PostMapping("/register")
    public String register(@RequestBody UserLoginDTO dto) {
        try {
            userService.register(dto);
            return "注册成功！";
        } catch (Exception e) {
            return "注册失败：" + e.getMessage();
        }
    }

    @PostMapping("/login")
    public Object login(@RequestBody UserLoginDTO dto) {
        try {
            return userService.login(dto);
        } catch (Exception e) {
            return "登录失败：" + e.getMessage();
        }
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<Map<String, Object>> profile(
            @PathVariable Long userId,
            @RequestParam(required = false) Long viewerId) {
        User user = userService.getById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        boolean ownProfile = viewerId != null && viewerId.equals(userId);
        List<?> uploads = videoService.getVideosByUserId(userId).stream()
                .filter(video -> ownProfile || "public".equals(video.getStatus()))
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("followers", 0);
        result.put("uploadCount", ownProfile ? videoService.countVideosByUserId(userId) : uploads.size());
        result.put("favoriteCount", videoService.countFavoriteVideosByUserId(userId));
        result.put("uploads", uploads);
        result.put("favorites", videoService.getFavoriteVideosByUserId(userId));
        return ApiResponse.success(result);
    }

    @PutMapping("/{userId}/avatar")
    public ApiResponse<Map<String, Object>> updateAvatar(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> requestBody) {
        User user = userService.getById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        Object avatarUrl = requestBody.get("avatarUrl");
        user.setAvatarUrl(avatarUrl == null ? "" : avatarUrl.toString());
        userService.updateById(user);

        Map<String, Object> result = new HashMap<>();
        result.put("avatarUrl", user.getAvatarUrl());
        return ApiResponse.success(result);
    }
}
