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
    void listRoomsFiltersByCategoryAndOnlyReturnsOnlineRooms() throws Exception {
        long categoryOneRoom = createRoom(10, "分类一直播", 1);
        createRoom(11, "分类二直播", 2);
        mockMvc.perform(post("/api/live/rooms/" + categoryOneRoom + "/close")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/live/rooms")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("categoryId", "2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertEquals(1, data.path("total").asInt());
        assertEquals(2, data.path("list").get(0).path("categoryId").asInt());
        assertEquals("online", data.path("list").get(0).path("status").asText());
    }

    @Test
    void roomDetailsExposeQualityUrlsAndMissingRoomReturnsBusinessError() throws Exception {
        long roomId = createRoom(10, "清晰度测试", null);

        String response = mockMvc.perform(get("/api/live/rooms/" + roomId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        assertEquals(roomId, data.path("roomId").asLong());
        assertEquals(data.path("pullUrl").asText(), data.path("qualityUrls").path("原画").asText());
        assertTrue(data.path("qualityUrls").path("720P").asText().endsWith("_720p.flv"));
        assertTrue(data.path("qualityUrls").path("480P").asText().endsWith("_480p.flv"));

        String missing = mockMvc.perform(get("/api/live/rooms/999999"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(404, objectMapper.readTree(missing).path("code").asInt());
    }

    @Test
    void validationAndOwnershipErrorsAreReturnedWithoutChangingRoomState() throws Exception {
        String invalid = mockMvc.perform(post("/api/live/rooms")
                        .header("X-User-Id", "10")
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(400, objectMapper.readTree(invalid).path("code").asInt());

        long roomId = createRoom(10, "权限测试", null);
        String wrongOwner = mockMvc.perform(post("/api/live/rooms/" + roomId + "/close")
                        .header("X-User-Id", "11"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(400, objectMapper.readTree(wrongOwner).path("code").asInt());
        assertEquals("online", room(roomId).path("status").asText());

        String missingUser = mockMvc.perform(post("/api/live/rooms/" + roomId + "/close"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(400, objectMapper.readTree(missingUser).path("code").asInt());
    }

    @Test
    void danmuHistoryAndLikeApiReturnPersistedValues() throws Exception {
        long roomId = createRoom(10, "历史测试", null);
        WsClient client = openWs(roomId);
        try {
            client.socket.sendText("{\"type\":\"danmu\",\"userId\":10,\"username\":\"主播\",\"content\":\"历史弹幕\",\"color\":\"#ff0000\"}", true).join();
            client.socket.sendText("{\"type\":\"like\",\"userId\":11}", true).join();
            await(() -> client.messages.stream().anyMatch(node -> "danmu".equals(node.path("type").asText())
                    && "历史弹幕".equals(node.path("content").asText())));

            String danmus = mockMvc.perform(get("/api/live/rooms/" + roomId + "/danmus")
                            .param("limit", "1"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode danmu = objectMapper.readTree(danmus).path("data").get(0);
            assertEquals("历史弹幕", danmu.path("content").asText());
            assertEquals("#ff0000", danmu.path("color").asText());

            String likes = mockMvc.perform(get("/api/live/rooms/" + roomId + "/like"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertEquals(1, objectMapper.readTree(likes).path("data").path("likeCount").asInt());
        } finally {
            client.socket.abort();
        }
    }

    @Test
    void srsHealthApiReportsProbeConfiguration() throws Exception {
        String response = mockMvc.perform(get("/api/live/srs/health"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        assertEquals(200, objectMapper.readTree(response).path("code").asInt());
        assertTrue(data.has("enabled"));
        assertTrue(data.has("reachable"));
        assertTrue(data.has("apiBaseUrl"));
    }

    @Test
    void websocketReturnsErrorForUnsupportedOrInvalidMessages() throws Exception {
        long roomId = createRoom(10, "异常消息测试", null);
        WsClient client = openWs(roomId);
        try {
            client.socket.sendText("{\"type\":\"unknown\",\"userId\":10}", true).join();
            await(() -> client.messages.stream().anyMatch(node -> "error".equals(node.path("type").asText())
                    && node.path("message").asText().contains("不支持")));

            client.socket.sendText("{\"type\":\"danmu\",\"userId\":10,\"content\":\"\"}", true).join();
            await(() -> client.messages.stream().filter(node -> "error".equals(node.path("type").asText()))
                    .anyMatch(node -> node.path("message").asText().contains("弹幕内容")));

            String likes = mockMvc.perform(get("/api/live/rooms/" + roomId + "/like"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertEquals(0, objectMapper.readTree(likes).path("data").path("likeCount").asInt());
        } finally {
            client.socket.abort();
        }
    }

    private long createRoom(long userId, String title, Integer categoryId) throws Exception {
        String category = categoryId == null ? "" : ",\"categoryId\":" + categoryId;
        String response = mockMvc.perform(post("/api/live/rooms")
                        .header("X-User-Id", String.valueOf(userId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"" + category + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("roomId").asLong();
    }

    private JsonNode room(long roomId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/live/rooms/" + roomId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test
    void actuatorEndpointsAreAvailableForDeploymentChecks() throws Exception {
        String health = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("UP", objectMapper.readTree(health).path("status").asText());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
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
