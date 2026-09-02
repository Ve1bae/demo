package com.example.video.model;

import java.util.Map;
import java.util.Set;

/**
 * 用户偏好：关注作者、兴趣标签、已看视频。用于个性化推荐打分。
 * 数据来自 user-service（通过 UserPreferenceClient 获取），获取失败时降级为 guest。
 */
public record UserPreference(Set<Long> followedAuthorIds,
                             Map<String, Integer> interests,
                             Set<Long> viewedVideoIds) {
    public static UserPreference guest() {
        return new UserPreference(Set.of(), Map.of(), Set.of());
    }
}
