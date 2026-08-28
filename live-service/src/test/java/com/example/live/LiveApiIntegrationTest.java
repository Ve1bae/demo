package com.example.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LiveApiIntegrationTest {
    @LocalServerPort
    private int port;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM live_danmu");
        jdbcTemplate.update("DELETE FROM room_likes");
        jdbcTemplate.update("DELETE FROM live_room");
    }

    @Test
    void liveRoomCrudAndOfflineGuard() throws Exception {
        String created = mockMvc.perform(post("/api/live/rooms")
                        .header("X-User-Id", "10")
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"集成测试直播间\",\"categoryId\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode room = objectMapper.readTree(created).path("data");
        long roomId = room.path("roomId").asLong();
        assertEquals("online", room.path("status").asText());
        assertTrue(room.path("pushUrl").asText().startsWith("rtmp://"));
        assertTrue(room.path("pullUrl").asText().endsWith(".flv"));

        String closed = mockMvc.perform(post("/api/live/rooms/" + roomId + "/close")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("offline", objectMapper.readTree(closed).path("data").path("status").asText());
        String danmu = mockMvc.perform(get("/api/live/rooms/" + roomId + "/danmus"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(200, objectMapper.readTree(danmu).path("code").asInt());
    }

    @Test
    void websocketBroadcastsDanmuAndLikeCount() throws Exception {
        JsonNode room = objectMapper.readTree(mockMvc.perform(post("/api/live/rooms")
                        .header("X-User-Id", "10")
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"WebSocket 测试\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        long roomId = room.path("roomId").asLong();
        WsClient clientA = openWs(roomId);
        WsClient clientB = openWs(roomId);
        try {
            clientA.socket.sendText("{\"type\":\"danmu\",\"userId\":10,\"username\":\"主播\",\"content\":\"晚上好\"}", true).join();
            clientA.socket.sendText("{\"type\":\"like\",\"userId\":11}", true).join();
            await(() -> clientB.messages.stream().anyMatch(node -> "danmu".equals(node.path("type").asText())
                    && "晚上好".equals(node.path("content").asText())));
            await(() -> clientB.messages.stream().anyMatch(node -> "like".equals(node.path("type").asText())
                    && node.path("likeCount").asLong() == 1));
            assertEquals(1, objectMapper.readTree(mockMvc.perform(get("/api/live/rooms/" + roomId + "/like"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                    .path("data").path("likeCount").asLong());
        } finally {
            clientA.socket.abort();
            clientB.socket.abort();
        }
    }

    private WsClient openWs(long roomId) throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        List<JsonNode> messages = new CopyOnWriteArrayList<>();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/live/" + roomId), new WebSocket.Listener() {
                    @Override public void onOpen(WebSocket webSocket) {
                        webSocket.request(1); opened.countDown();
                    }
                    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        if (last) try { messages.add(objectMapper.readTree(data.toString())); } catch (Exception ignored) { }
                        webSocket.request(1); return CompletableFuture.completedFuture(null);
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

    private record WsClient(WebSocket socket, List<JsonNode> messages) { }
}
