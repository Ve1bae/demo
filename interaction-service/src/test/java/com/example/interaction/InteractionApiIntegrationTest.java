package com.example.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InteractionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM interaction_notification");
        jdbcTemplate.update("DELETE FROM interaction_dynamic_mention");
        jdbcTemplate.update("DELETE FROM interaction_dynamic");
    }

    @Test
    void publishMentionReadNotificationEndToEnd() throws Exception {
        String response = mockMvc.perform(post("/api/interactions/dynamics")
                        .header("X-User-Id", "10")
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"  欢迎来看直播  \",\"mentionedUserIds\":[11,10,11,12]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(response);
        assertEquals(200, created.path("code").asInt(), response);
        assertEquals("欢迎来看直播", created.path("data").path("content").asText());
        assertEquals(2, created.path("data").path("mentionedUserIds").size());

        String notifications = mockMvc.perform(get("/api/interactions/notifications")
                        .header("X-User-Id", "11")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode notificationBody = objectMapper.readTree(notifications);
        assertEquals(1, notificationBody.path("data").size(), notifications);
        long notificationId = notificationBody.path("data").get(0).path("id").asLong();
        assertEquals(10L, notificationBody.path("data").get(0).path("actorUserId").asLong());

        mockMvc.perform(post("/api/interactions/notifications/" + notificationId + "/read")
                        .header("X-User-Id", "11"))
                .andExpect(status().isOk());

        String unreadCount = mockMvc.perform(get("/api/interactions/notifications/unread-count")
                        .header("X-User-Id", "11"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, objectMapper.readTree(unreadCount).path("data").path("unreadCount").asInt());
    }

    @Test
    void unauthenticatedPublishAndBlankContentAreRejected() throws Exception {
        String missingUser = mockMvc.perform(post("/api/interactions/dynamics")
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"动态\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(400, objectMapper.readTree(missingUser).path("code").asInt());

        String blankContent = mockMvc.perform(post("/api/interactions/dynamics")
                        .header("X-User-Id", "10")
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode error = objectMapper.readTree(blankContent);
        assertEquals(400, error.path("code").asInt());
        assertTrue(error.path("message").asText().contains("不能为空"), blankContent);
    }
}
