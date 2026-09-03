package com.example.live;

import com.example.live.dto.CreateLiveRoomRequest;
import com.example.live.model.LiveRoomView;
import com.example.live.model.LiveDanmuView;
import com.example.live.repository.LiveRepository;
import com.example.live.service.LiveService;
import com.example.live.service.SrsHealthService;
import com.example.live.service.LiveTranscodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveServiceUnitTest {
    @Mock
    private LiveRepository repository;
    @Mock
    private SrsHealthService srsHealthService;
    @Mock
    private LiveTranscodeService transcodeService;

    @Test
    void createRoomBuildsPushAndPullUrls() {
        LiveService service = new LiveService(repository, srsHealthService, transcodeService);
        when(repository.insertRoom(eq(10L), eq(2L), eq("测试直播"), any(), any(), any(), eq(null)))
                .thenReturn(7L);
        LiveRoomView saved = new LiveRoomView(7L, 10L, 2L, "测试直播", "room", "rtmp://push",
                "http://pull.flv", Map.of(), null, "online", null, null, null);
        when(repository.findRoom(7L)).thenReturn(saved);
        when(srsHealthService.probeEnabled()).thenReturn(false);

        LiveRoomView result = service.createRoom(10L, new CreateLiveRoomRequest("  测试直播 ", 2L, null));

        assertEquals(7L, result.roomId());
        verify(repository).resetInteraction(7L);
    }

    @Test
    void invalidUserCannotCreateRoom() {
        LiveService service = new LiveService(repository, srsHealthService, transcodeService);
        assertThrows(IllegalArgumentException.class,
                () -> service.createRoom(null, new CreateLiveRoomRequest("直播", null, null)));
    }

    @Test
    void invalidDanmuIsRejectedBeforeInsert() {
        LiveService service = new LiveService(repository, srsHealthService, transcodeService);
        LiveRoomView room = new LiveRoomView(1L, 10L, null, "直播", "room", "push", "pull",
                Map.of(), null, "online", null, null, null);
        when(repository.findRoom(1L)).thenReturn(room);

        assertThrows(IllegalArgumentException.class,
                () -> service.addDanmu(1L, 10L, "用户", "x".repeat(256), null));
    }

    @Test
    void pageParametersAreClamped() {
        LiveService service = new LiveService(repository, srsHealthService, transcodeService);
        when(repository.findRooms(null, 100, 0)).thenReturn(List.of());
        when(repository.countRooms(null)).thenReturn(0L);

        service.listRooms(0, 1000, null);

        verify(repository).findRooms(null, 100, 0);
    }

    @Test
    void onlyRoomOwnerCanCloseOnlineRoom() {
        LiveService service = new LiveService(repository, srsHealthService, transcodeService);
        LiveRoomView room = onlineRoom(1L, 10L);
        when(repository.findRoom(1L)).thenReturn(room);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.closeRoom(1L, 11L));

        assertEquals("只能关闭自己的直播间", exception.getMessage());
        verify(repository, org.mockito.Mockito.never()).closeRoom(any(), any());
    }

    @Test
    void offlineRoomRejectsDanmuAndLike() {
        LiveService service = new LiveService(repository, srsHealthService, transcodeService);
        when(repository.findRoom(1L)).thenReturn(new LiveRoomView(1L, 10L, null, "直播", "room",
                "push", "pull.flv", Map.of(), null, "offline", null, null, null));

        assertEquals("直播间未开播或已结束", assertThrows(IllegalArgumentException.class,
                () -> service.addLike(1L, 11L)).getMessage());
        assertEquals("直播间未开播或已结束", assertThrows(IllegalArgumentException.class,
                () -> service.addDanmu(1L, 11L, "用户", "内容", null)).getMessage());
        verify(repository, org.mockito.Mockito.never()).addLike(any());
        verify(repository, org.mockito.Mockito.never()).insertDanmu(any(), any(), any(), any(), any());
    }

    @Test
    void danmuUsesFallbackUsernameAndColor() {
        LiveService service = new LiveService(repository, srsHealthService, transcodeService);
        when(repository.findRoom(1L)).thenReturn(onlineRoom(1L, 10L));
        LiveDanmuView saved = new LiveDanmuView(9L, 1L, 11L, "用户 11", "你好", "#ffffff", null);
        when(repository.insertDanmu(1L, 11L, "用户 11", "你好", "#ffffff")).thenReturn(saved);

        LiveDanmuView result = service.addDanmu(1L, 11L, " ", "  你好  ", " ");

        assertEquals("用户 11", result.username());
        assertEquals("#ffffff", result.color());
        verify(repository).insertDanmu(1L, 11L, "用户 11", "你好", "#ffffff");
    }

    @Test
    void likeRequiresPositiveUserIdBeforeDatabaseWrite() {
        LiveService service = new LiveService(repository, srsHealthService, transcodeService);
        when(repository.findRoom(1L)).thenReturn(onlineRoom(1L, 10L));

        assertThrows(IllegalArgumentException.class, () -> service.addLike(1L, 0L));

        verify(repository, org.mockito.Mockito.never()).addLike(any());
    }

    private LiveRoomView onlineRoom(Long roomId, Long userId) {
        return new LiveRoomView(roomId, userId, null, "直播", "room", "push",
                "pull.flv", Map.of(), null, "online", null, null, null);
    }
}
