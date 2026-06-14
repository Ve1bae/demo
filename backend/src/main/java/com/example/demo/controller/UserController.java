package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.entity.User;
import com.example.demo.entity.UserFollow;
import com.example.demo.entity.ViewHistory;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserFollowMapper;
import com.example.demo.mapper.ViewHistoryMapper;
import com.example.demo.service.UserService;
import com.example.demo.service.VideoService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private UserFollowMapper userFollowMapper;

    @Autowired
    private ViewHistoryMapper viewHistoryMapper;

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
        result.put("bio", user.getBio());
        result.put("followingCount", countFollowing(userId));
        result.put("followerCount", countFollowers(userId));
        result.put("following", viewerId != null && isFollowing(viewerId, userId));
        result.put("followers", countFollowers(userId));
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

    @PostMapping("/{userId}/follow")
    public ApiResponse<String> followUser(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        if (currentUserId == null || userService.getById(currentUserId) == null) {
            return ApiResponse.error(401, "请先登录后关注用户");
        }
        if (Objects.equals(currentUserId, userId)) {
            return ApiResponse.error(400, "不能关注自己");
        }
        if (userService.getById(userId) == null) {
            return ApiResponse.error(404, "目标用户不存在");
        }
        UserFollow relation = new UserFollow();
        relation.setUserId(currentUserId);
        relation.setFollowUserId(userId);
        try {
            userFollowMapper.insert(relation);
        } catch (DuplicateKeyException ignored) {
            return ApiResponse.success("已经关注过该用户", null);
        }
        return ApiResponse.success("关注成功", null);
    }

    @DeleteMapping("/{userId}/follow")
    public ApiResponse<String> unfollowUser(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        if (currentUserId == null || userService.getById(currentUserId) == null) {
            return ApiResponse.error(401, "请先登录后取消关注");
        }
        QueryWrapper<UserFollow> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", currentUserId).eq("follow_user_id", userId);
        userFollowMapper.delete(wrapper);
        return ApiResponse.success("取消关注成功", null);
    }

    @GetMapping("/{userId}/following")
    public ApiResponse<List<Map<String, Object>>> getFollowingList(@PathVariable Long userId) {
        List<Map<String, Object>> result = userFollowMapper.selectList(new QueryWrapper<UserFollow>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at"))
                .stream()
                .map(relation -> buildUserSummary(relation.getFollowUserId(), userId))
                .filter(Objects::nonNull)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{userId}/followers")
    public ApiResponse<List<Map<String, Object>>> getFollowerList(@PathVariable Long userId) {
        List<Map<String, Object>> result = userFollowMapper.selectList(new QueryWrapper<UserFollow>()
                        .eq("follow_user_id", userId)
                        .orderByDesc("created_at"))
                .stream()
                .map(relation -> buildUserSummary(relation.getUserId(), userId))
                .filter(Objects::nonNull)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{userId}/history")
    public ApiResponse<List<ViewHistory>> getViewHistory(@PathVariable Long userId) {
        List<ViewHistory> histories = viewHistoryMapper.selectList(new QueryWrapper<ViewHistory>()
                .eq("user_id", userId)
                .orderByDesc("last_viewed_at"));
        histories.forEach(history -> history.setVideo(videoService.getVideoById(history.getVideoId())));
        return ApiResponse.success(histories);
    }

    private Map<String, Object> buildUserSummary(Long userId, Long viewerId) {
        User user = userService.getById(userId);
        if (user == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("bio", user.getBio());
        result.put("followingCount", countFollowing(userId));
        result.put("followerCount", countFollowers(userId));
        result.put("following", viewerId != null && isFollowing(viewerId, userId));
        return result;
    }

    private long countFollowing(Long userId) {
        return userFollowMapper.selectCount(new QueryWrapper<UserFollow>().eq("user_id", userId));
    }

    private long countFollowers(Long userId) {
        return userFollowMapper.selectCount(new QueryWrapper<UserFollow>().eq("follow_user_id", userId));
    }

    private boolean isFollowing(Long viewerId, Long targetUserId) {
        if (viewerId == null || targetUserId == null || Objects.equals(viewerId, targetUserId)) {
            return false;
        }
        return userFollowMapper.selectCount(new QueryWrapper<UserFollow>()
                .eq("user_id", viewerId)
                .eq("follow_user_id", targetUserId)) > 0;
    }
}
