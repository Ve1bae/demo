package com.example.video.client;

import com.example.video.model.UserPreference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "user-service", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopUserPreferenceClient implements UserPreferenceClient {
    @Override
    public UserPreference getPreference(Long userId) {
        return UserPreference.guest();
    }
}
