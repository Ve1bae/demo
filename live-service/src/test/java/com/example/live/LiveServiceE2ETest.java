package com.example.live;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end regression from an external HTTP/WebSocket client through the running service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveServiceE2ETest {
    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM live_danmu");
        jdbcTemplate.update("DELETE FROM room_likes");
        jdbcTemplate.update("DELETE FROM live_room");
    }

    @Test
    void viewerCanOpenRoomInteractAndSeeFinalRoomState() throws Exception {
        JsonNode created = body(send("POST", "/api/live/rooms", "10",
                "{\"title\":\"端到端直播间\",\"categoryId\":3}"));
        assertEquals(200, created.path("code").asInt());
        long roomId = created.path("data").path("roomId").asLong();
        assertEquals("online", created.path("data").path("status").asText());

        JsonNode room = body(send("GET", "/api/live/rooms/" + roomId, null, null));
        assertEquals(200, room.path("code").asInt());
        assertTrue(room.path("data").path("pullUrl").asText().endsWith(".flv"));

        WsClient viewer = openWs(roomId);
        try {
            viewer.socket.sendText("{\"type\":\"danmu\",\"userId\":20,\"username\":\"观众\",\"content\":\"大家好\"}", true).join();
            viewer.socket.sendText("{\"type\":\"like\",\"userId\":20}", true).join();
            await(() -> viewer.messages.stream().anyMatch(message -> "danmu".equals(message.path("type").asText())
                    && "大家好".equals(message.path("content").asText())));
            await(() -> viewer.messages.stream().anyMatch(message -> "like".equals(message.path("type").asText())
                    && message.path("likeCount").asInt() == 1));

            JsonNode danmus = body(send("GET", "/api/live/rooms/" + roomId + "/danmus?limit=10", null, null));
            assertEquals("大家好", danmus.path("data").get(0).path("content").asText());
            JsonNode likes = body(send("GET", "/api/live/rooms/" + roomId + "/like", null, null));
            assertEquals(1, likes.path("data").path("likeCount").asInt());

            JsonNode closed = body(send("POST", "/api/live/rooms/" + roomId + "/close", "10", null));
            assertEquals(200, closed.path("code").asInt());
            assertEquals("offline", closed.path("data").path("status").asText());

            JsonNode finalRoom = body(send("GET", "/api/live/rooms/" + roomId, null, null));
            assertEquals("offline", finalRoom.path("data").path("status").asText());
        } finally {
            viewer.socket.abort();
        }
    }

    private HttpResponse<String> send(String method, String path, String userId, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5));
        if (userId != null) builder.header("X-User-Id", userId);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private WsClient openWs(long roomId) throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        List<JsonNode> messages = new CopyOnWriteArrayList<>();
        WebSocket socket = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/live/" + roomId), new WebSocket.Listener() {
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
                                // Malformed frames are irrelevant to this end-to-end assertion.
                            }
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }
                }).get(5, TimeUnit.SECONDS);
        assertTrue(opened.await(5, TimeUnit.SECONDS));
        return new WsClient(socket, messages);
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("WebSocket message not received");
            Thread.sleep(50);
        }
    }

    private record WsClient(WebSocket socket, List<JsonNode> messages) {
    }
}
