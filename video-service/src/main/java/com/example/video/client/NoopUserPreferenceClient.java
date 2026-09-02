package com.example.video.client;

import com.example.video.model.UserPreference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 降级实现：未开启 user-service 调用时使用（user-service.enabled=false 或未配置）。
 * 不访问任何外部服务，推荐退化为「按热度 + 本地已看」排序。
 */
@Component
@ConditionalOnProperty(prefix = "user-service", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopUserPreferenceClient implements UserPreferenceClient {
    @Override
    public UserPreference getPreference(Long userId) {
        return UserPreference.guest();
    }
}
