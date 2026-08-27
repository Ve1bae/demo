package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void createTestUser() {
        jdbcTemplate.update(
                "INSERT IGNORE INTO sys_user (username, password, nickname) VALUES ('livetester', 'x', '直播测试用户')"
        );
    }

    @Test
    void fullLiveBusinessFlow() throws Exception {
        long roomId = createRoom();

        JsonNode detail = request("GET", "/live/rooms/" + roomId, null, null);
        assertEquals("online", detail.path("data").path("status").asText());
        assertFalse(detail.path("data").path("pullUrl").asText().isBlank());
        assertFalse(detail.path("data").path("qualityUrls").path("原画").asText().isBlank());

        WsClient viewerA = openWs(roomId);
        WsClient viewerB = openWs(roomId);
        try {
            awaitTrue(() -> hasType(viewerA, "online_count") && hasType(viewerB, "online_count"), 8);

            send(viewerA, "{\"type\":\"danmu\",\"userId\":1,\"username\":\"测试用户\",\"content\":\"主播晚上好\",\"color\":\"#ffffff\"}");
            awaitTrue(() -> hasDanmu(viewerB, "主播晚上好"), 8);

            JsonNode history = request("GET", "/live/rooms/" + roomId + "/danmus?limit=50", null, null);
            assertTrue(history.path("data").toString().contains("主播晚上好"), history.toString());

            int danmuBefore = danmuCount(viewerB);
            send(viewerA, "{\"type\":\"danmu\",\"userId\":1,\"username\":\"测试用户\",\"content\":\"   \",\"color\":\"#ffffff\"}");
            Thread.sleep(400);
            assertEquals(danmuBefore, danmuCount(viewerB));

            String overlong = "x".repeat(300);
            send(viewerA, "{\"type\":\"danmu\",\"userId\":1,\"username\":\"测试用户\",\"content\":\"" + overlong + "\",\"color\":\"#ffffff\"}");
            Thread.sleep(400);
            assertEquals(danmuBefore, danmuCount(viewerB));
            assertFalse(viewerA.socket.isOutputClosed(), "overlong danmu must not close viewer A");

            long initialLikes = getLikeCount(roomId);
            send(viewerA, "{\"type\":\"like\",\"userId\":1}");
            awaitTrue(() -> latestLikeCount(viewerB) != null && latestLikeCount(viewerB) == initialLikes + 1, 8);

            for (int i = 0; i < 10; i += 1) {
                send(viewerA, "{\"type\":\"like\",\"userId\":1}");
            }
            awaitTrue(() -> getLikeCountUnchecked(roomId) == initialLikes + 11, 8);
        } finally {
            closeQuietly(viewerA);
            closeQuietly(viewerB);
        }

        JsonNode closed = request("POST", "/live/rooms/" + roomId + "/close", null, "1");
        assertEquals("offline", closed.path("data").path("status").asText());

        JsonNode missing = request("GET", "/live/rooms/999999", null, null);
        assertEquals(404, missing.path("code").asInt(), missing.toString());
    }

    private long createRoom() throws Exception {
        JsonNode created = request(
                "POST",
                "/live/rooms",
                "{\"title\":\"端到端测试直播间\",\"categoryId\":1,\"coverUrl\":\"\"}",
                "1"
        );
        assertEquals(200, created.path("code").asInt(), created.toString());
        return created.path("data").path("roomId").asLong();
    }

    private long getLikeCount(long roomId) throws Exception {
        JsonNode response = request("GET", "/live/rooms/" + roomId + "/like", null, null);
        assertEquals(200, response.path("code").asInt(), response.toString());
        return response.path("data").path("likeCount").asLong();
    }

    private long getLikeCountUnchecked(long roomId) {
        try {
            return getLikeCount(roomId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode request(String method, String path, String body, String userId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10));
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private WsClient openWs(long roomId) throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        List<JsonNode> messages = new CopyOnWriteArrayList<>();
        CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl(roomId)), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                        opened.countDown();
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        if (last) {
                            try {
                                messages.add(objectMapper.readTree(data.toString()));
                            } catch (Exception ignored) {
                                // ignore malformed message
                            }
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        opened.countDown();
                    }
                });
        WebSocket socket = future.get(5, TimeUnit.SECONDS);
        assertTrue(opened.await(5, TimeUnit.SECONDS), "WebSocket did not open");
        return new WsClient(socket, messages);
    }

    private void send(WsClient client, String payload) {
        client.socket.sendText(payload, true).join();
    }

    private void closeQuietly(WsClient client) {
        if (client != null) {
            try {
                client.socket.abort();
            } catch (Exception ignored) {
                // already closed
            }
        }
    }

    private boolean hasType(WsClient client, String type) {
        return client.messages.stream().anyMatch(message -> type.equals(message.path("type").asText()));
    }

    private boolean hasDanmu(WsClient client, String content) {
        return client.messages.stream().anyMatch(message ->
                "danmu".equals(message.path("type").asText())
                        && content.equals(message.path("content").asText())
        );
    }

    private int danmuCount(WsClient client) {
        return (int) client.messages.stream()
                .filter(message -> "danmu".equals(message.path("type").asText()))
                .count();
    }

    private Long latestLikeCount(WsClient client) {
        Long latest = null;
        for (JsonNode message : client.messages) {
            if ("like".equals(message.path("type").asText())) {
                latest = message.path("likeCount").asLong();
            }
        }
        return latest;
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutSeconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                fail("condition not met within " + timeoutSeconds + "s");
            }
            Thread.sleep(100);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port + "/api";
    }

    private String wsUrl(long roomId) {
        return "ws://127.0.0.1:" + port + "/ws/live/" + roomId;
    }

    private static final class WsClient {
        private final WebSocket socket;
        private final List<JsonNode> messages;

        private WsClient(WebSocket socket, List<JsonNode> messages) {
            this.socket = socket;
            this.messages = messages;
        }
    }
}
