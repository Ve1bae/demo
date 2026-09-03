package com.example.demo.uc07;

import com.example.demo.common.ApiResponse;
import com.example.demo.controller.LiveRoomController;
import com.example.demo.dto.LiveRoomCreateDTO;
import com.example.demo.service.LiveRoomService;
import com.example.demo.vo.LiveRoomVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-07 页面提交到控制器的 API 级测试。
 * 验证顺序图中的 POST /api/live/rooms、身份请求头、成功响应和异常响应。
 */
@ExtendWith(MockitoExtension.class)
class Uc07CreateRoomControllerTest {

    @Mock
    private LiveRoomService liveRoomService;

    @InjectMocks
    private LiveRoomController controller;

    @Test
    void postCreateRoom_shouldPassRequestToService_andReturnRoomData() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        LiveRoomVO room = new LiveRoomVO();
        room.setRoomId(7001L);
        room.setUserId(42L);
        room.setTitle("UC-07 API 测试直播间");
        room.setStreamName("room_uc07");
        room.setPushUrl("rtmp://test/live/room_uc07");
        room.setPullUrl("http://test:8081/live/room_uc07.flv");
        room.setStatus("online");
        room.setQualityUrls(new LinkedHashMap<>());
        when(liveRoomService.createRoom(any(LiveRoomCreateDTO.class), eq(42L)))
                .thenReturn(room);

        mockMvc.perform(post("/api/live/rooms")
                        .header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "UC-07 API 测试直播间",
                                  "categoryId": 8,
                                  "coverUrl": "https://test-host/cover.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.roomId", is(7001)))
                .andExpect(jsonPath("$.data.userId", is(42)))
                .andExpect(jsonPath("$.data.status", is("online")))
                .andExpect(jsonPath("$.data.pushUrl", notNullValue()))
                .andExpect(jsonPath("$.data.pullUrl", notNullValue()));

        verify(liveRoomService).createRoom(any(LiveRoomCreateDTO.class), eq(42L));
    }

    @Test
    void postCreateRoom_shouldReturn400_whenServiceRejectsInvalidRequest() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(liveRoomService.createRoom(any(LiveRoomCreateDTO.class), eq(42L)))
                .thenThrow(new IllegalArgumentException("直播间标题不能为空"));

        mockMvc.perform(post("/api/live/rooms")
                        .header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("直播间标题不能为空")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(liveRoomService).createRoom(any(LiveRoomCreateDTO.class), eq(42L));
    }
}
