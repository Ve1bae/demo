package com.example.video.client;

import com.example.video.model.UserPreference;

/**
 * 跨服务获取用户偏好：从 user-service 拉取关注作者、兴趣标签、已看视频。
 */
public interface UserPreferenceClient {
    UserPreference getPreference(Long userId);
}
