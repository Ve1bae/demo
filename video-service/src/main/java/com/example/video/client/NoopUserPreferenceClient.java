package com.example.video.client;

import com.example.video.model.UserPreference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(UserPreferenceClient.class)
public class NoopUserPreferenceClient implements UserPreferenceClient {
    @Override
    public UserPreference getPreference(Long userId) {
        return UserPreference.guest();
    }
}
