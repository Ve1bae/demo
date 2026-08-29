package com.example.video.client;

import com.example.video.model.UserPreference;

public interface UserPreferenceClient {
    UserPreference getPreference(Long userId);
}
