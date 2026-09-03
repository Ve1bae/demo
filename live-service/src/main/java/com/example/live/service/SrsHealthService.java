package com.example.live.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SrsHealthService {
    private static final int SRS_CONNECT_TIMEOUT_MS = 1000;
    private static final int SRS_READ_TIMEOUT_MS = 2000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${live.srs.api-base-url:http://localhost:1985}")
    private String apiBaseUrl = "http://localhost:1985";

    @Value("${live.srs.probe-enabled:false}")
    private boolean probeEnabled;

    public SrsHealthService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(SRS_CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(SRS_READ_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean probeEnabled() {
        return probeEnabled;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", probeEnabled);
        result.put("reachable", isReachable());
        result.put("apiBaseUrl", apiBaseUrl);
        return result;
    }

    public boolean isStreamActive(String streamName) {
        if (!probeEnabled || streamName == null) return false;
        try {
            String body = restClient.get().uri(apiBaseUrl + "/api/v1/streams/").retrieve().body(String.class);
            JsonNode streams = objectMapper.readTree(body).path("streams");
            if (!streams.isArray()) return false;
            for (JsonNode stream : streams) {
                if (streamName.equals(stream.path("name").asText())
                        && stream.path("publish").path("active").asBoolean(false)) return true;
            }
        } catch (Exception ignored) {
            // SRS availability is exposed by health; playback URLs remain available for retry.
        }
        return false;
    }

    private boolean isReachable() {
        try {
            restClient.get().uri(apiBaseUrl + "/api/v1/versions").retrieve().toBodilessEntity();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
