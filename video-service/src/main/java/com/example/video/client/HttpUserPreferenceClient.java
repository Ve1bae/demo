package com.example.video.client;

import com.example.video.model.UserPreference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 真实实现：通过 HTTP 调用 user-service 获取用户偏好。
 * 仅在 user-service.enabled=true 时生效；调用失败/超时/接口不存在时降级为 guest，不阻塞推荐。
 */
@Component
@ConditionalOnProperty(prefix = "user-service", name = "enabled", havingValue = "true")
public class HttpUserPreferenceClient implements UserPreferenceClient {
    private static final Logger log = LoggerFactory.getLogger(HttpUserPreferenceClient.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpUserPreferenceClient(org.springframework.core.env.Environment environment) {
        String baseUrl = environment.getProperty("user-service.base-url");
        long timeoutMillis = environment.getProperty("user-service.timeout-millis", Long.class, 500L);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public UserPreference getPreference(Long userId) {
        if (userId == null) {
            return UserPreference.guest();
        }
        try {
            String responseBody = restClient.get()
                    .uri("/api/users/{userId}/preferences", userId)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root != null && root.has("data") ? root.path("data") : root;
            UserPreference preference = new UserPreference(readLongSet(data.path("followedAuthorIds")),
                    readInterests(data.path("interests")),
                    readLongSet(data.path("viewedVideoIds")));
            log.info("Loaded user preference: userId={}, followedAuthors={}, interests={}", userId,
                    preference.followedAuthorIds().size(), preference.interests().size());
            return preference;
        } catch (Exception exception) {
            // 跨服务调用失败：降级为无偏好，推荐退化为按热度排序
            log.warn("User preference unavailable for userId={}, using guest ranking: {}", userId, exception.toString());
            return UserPreference.guest();
        }
    }

    private Set<Long> readLongSet(JsonNode node) {
        Set<Long> values = new HashSet<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> {
                if (value.canConvertToLong()) values.add(value.longValue());
            });
        }
        return values;
    }

    private Map<String, Integer> readInterests(JsonNode node) {
        Map<String, Integer> values = new HashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue().canConvertToInt()) values.put(entry.getKey(), entry.getValue().intValue());
            });
        }
        return values;
    }
}
