package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.UserFollow;
import com.example.demo.entity.UserInterest;
import com.example.demo.mapper.UserFollowMapper;
import com.example.demo.mapper.UserInterestMapper;
import com.example.demo.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * user-service 对 video-service 暴露的最小偏好契约。
 * 观看历史由 video-service 自己维护，因此这里只返回关注作者与兴趣标签。
 */
@RestController
@RequestMapping("/api/users")
public class UserPreferenceController {
    private final UserService userService;
    private final UserFollowMapper userFollowMapper;
    private final UserInterestMapper userInterestMapper;

    public UserPreferenceController(UserService userService,
                                    UserFollowMapper userFollowMapper,
                                    UserInterestMapper userInterestMapper) {
        this.userService = userService;
        this.userFollowMapper = userFollowMapper;
        this.userInterestMapper = userInterestMapper;
    }

    @GetMapping("/{userId}/preferences")
    public ApiResponse<Map<String, Object>> preferences(@PathVariable Long userId) {
        if (userId == null || userId <= 0 || userService.getById(userId) == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        Set<Long> followedAuthorIds = userFollowMapper.selectList(new QueryWrapper<UserFollow>()
                        .eq("user_id", userId))
                .stream().map(UserFollow::getFollowUserId).collect(Collectors.toSet());
        Map<String, Integer> interests = userInterestMapper.selectList(new QueryWrapper<UserInterest>()
                        .eq("user_id", userId).orderByDesc("score"))
                .stream().filter(item -> item.getTag() != null)
                .collect(Collectors.toMap(UserInterest::getTag,
                        item -> item.getScore() == null ? 0 : item.getScore(),
                        Integer::max, LinkedHashMap::new));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("followedAuthorIds", followedAuthorIds);
        result.put("interests", interests);
        result.put("viewedVideoIds", List.of());
        return ApiResponse.success(result);
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<Map<String, Object>> profile(@PathVariable Long userId) {
        var user = userService.getById(userId);
        if (user == null) return ApiResponse.error(404, "用户不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("bio", user.getBio());
        result.put("followingCount", userFollowMapper.selectCount(new QueryWrapper<UserFollow>().eq("user_id", userId)));
        result.put("followerCount", userFollowMapper.selectCount(new QueryWrapper<UserFollow>().eq("follow_user_id", userId)));
        return ApiResponse.success(result);
    }
}
