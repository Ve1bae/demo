package com.example.video.client;

import com.example.video.model.UserPreference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "user-service", name = "base-url")
public class HttpUserPreferenceClient implements UserPreferenceClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpUserPreferenceClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                    org.springframework.core.env.Environment environment) {
        String baseUrl = environment.getProperty("user-service.base-url");
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public UserPreference getPreference(Long userId) {
        if (userId == null) {
            return UserPreference.guest();
        }
        try {
            JsonNode root = restClient.get()
                    .uri("/api/users/{userId}/preferences", userId)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = root != null && root.has("data") ? root.path("data") : root;
            return new UserPreference(readLongSet(data.path("followedAuthorIds")),
                    readInterests(data.path("interests")),
                    readLongSet(data.path("viewedVideoIds")));
        } catch (RuntimeException exception) {
            return UserPreference.guest();
        }
    }

    private Set<Long> readLongSet(JsonNode node) {
        Set<Long> values = new HashSet<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> {
                if (value.canConvertToLong()) {
                    values.add(value.longValue());
                }
            });
        }
        return values;
    }

    private Map<String, Integer> readInterests(JsonNode node) {
        Map<String, Integer> values = new HashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue().canConvertToInt()) {
                    values.put(entry.getKey(), entry.getValue().intValue());
                }
            });
        }
        return values;
    }
}
