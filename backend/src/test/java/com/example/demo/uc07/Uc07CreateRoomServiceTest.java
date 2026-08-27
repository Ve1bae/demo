package com.example.demo.uc07;

import com.example.demo.dto.LiveRoomCreateDTO;
import com.example.demo.entity.LiveRoom;
import com.example.demo.entity.User;
import com.example.demo.mapper.LiveRoomMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.LiveDanmuService;
import com.example.demo.service.LiveTranscodeService;
import com.example.demo.service.RoomLikesService;
import com.example.demo.service.impl.LiveRoomServiceImpl;
import com.example.demo.vo.LiveRoomVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-07 创建直播间服务层测试。
 *
 * 覆盖详细设计顺序图中的核心交互：参数校验、直播间对象创建、数据库插入、
 * 推流/播放地址生成、数据库更新、VO 转换、互动数据重置和转码任务启动。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Uc07CreateRoomServiceTest {

    @Mock
    private LiveRoomMapper liveRoomMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private LiveDanmuService liveDanmuService;

    @Mock
    private RoomLikesService roomLikesService;

    @Mock
    private LiveTranscodeService liveTranscodeService;

    private LiveRoomServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new LiveRoomServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", liveRoomMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "liveDanmuService", liveDanmuService);
        ReflectionTestUtils.setField(service, "roomLikesService", roomLikesService);
        ReflectionTestUtils.setField(service, "liveTranscodeService", liveTranscodeService);
        ReflectionTestUtils.setField(service, "rtmpBaseUrl", "rtmp://test-host/live/");
        ReflectionTestUtils.setField(service, "httpBaseUrl", "http://test-host:8081/");

        when(liveRoomMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(liveRoomMapper.insert(any(LiveRoom.class))).thenAnswer(invocation -> {
            LiveRoom room = invocation.getArgument(0);
            room.setId(7001L);
            return 1;
        });
        when(liveRoomMapper.updateById(any(LiveRoom.class))).thenReturn(1);

        User anchor = new User();
        anchor.setId(42L);
        anchor.setUsername("anchor42");
        anchor.setNickname("测试主播");
        when(userMapper.selectById(42L)).thenReturn(anchor);
    }

    @Test
    void createRoom_shouldFollowUc07InteractionOrder_andReturnCompleteRoomVo() {
        LiveRoomCreateDTO dto = new LiveRoomCreateDTO();
        dto.setTitle("UC-07 测试直播间");
        dto.setCategoryId(8L);
        dto.setCoverUrl("https://test-host/cover.png");

        LiveRoomVO result = service.createRoom(dto, 42L);

        assertNotNull(result, "服务应返回直播间 VO");
        assertEquals(7001L, result.getRoomId(), "数据库插入后应回填直播间编号");
        assertEquals(42L, result.getUserId(), "直播间应绑定当前主播");
        assertEquals("UC-07 测试直播间", result.getTitle());
        assertEquals("测试主播", result.getAnchorNickname(), "VO 应补充主播昵称");
        assertEquals("online", result.getStatus(), "创建成功后直播间应处于 online 状态");
        assertTrue(result.getStreamName().startsWith("room_"), "应生成可用的流名称");
        assertTrue(result.getPushUrl().startsWith("rtmp://test-host/live/"), "应生成 RTMP 推流地址");
        assertTrue(result.getPullUrl().startsWith("http://test-host:8081/live/"), "应生成 HTTP-FLV 播放地址");
        assertEquals(3, result.getQualityUrls().size(), "应返回原画、720P、480P 三档播放地址");

        // 通过插入和更新参数分别验证“创建对象”和“补充地址/状态”的两个阶段。
        org.mockito.ArgumentCaptor<LiveRoom> roomCaptor = org.mockito.ArgumentCaptor.forClass(LiveRoom.class);
        verify(liveRoomMapper).insert(roomCaptor.capture());
        LiveRoom insertedRoom = roomCaptor.getValue();
        assertEquals(42L, insertedRoom.getUserId());
        assertEquals(dto.getTitle(), insertedRoom.getTitle());
        assertEquals(dto.getCoverUrl(), insertedRoom.getCoverUrl());
        assertNotNull(insertedRoom.getStreamName(), "插入前应生成 streamName");

        InOrder order = inOrder(liveRoomMapper, liveDanmuService, roomLikesService, liveTranscodeService, userMapper);
        order.verify(liveRoomMapper).selectList(any());
        order.verify(liveRoomMapper).insert(any(LiveRoom.class));
        order.verify(liveDanmuService).clearRoomDanmu(7001L);
        order.verify(roomLikesService).resetLikeCount(7001L);
        order.verify(liveTranscodeService).start(
                7001L,
                insertedRoom.getPushUrl(),
                "rtmp://test-host/live/" + insertedRoom.getStreamName() + "_480p",
                "rtmp://test-host/live/" + insertedRoom.getStreamName() + "_720p");
        order.verify(userMapper).selectById(42L);
    }

    @Test
    void createRoom_shouldUpdateExistingRoom_andCloseOtherOnlineRooms() {
        doReturn(true).when(service).updateBatchById(any());

        LiveRoom existingRoom = new LiveRoom();
        existingRoom.setId(9001L);
        existingRoom.setUserId(42L);
        existingRoom.setStreamName("room_keep");
        existingRoom.setStatus("offline");

        LiveRoom otherOnlineRoom = new LiveRoom();
        otherOnlineRoom.setId(9002L);
        otherOnlineRoom.setUserId(42L);
        otherOnlineRoom.setStreamName("room_old");
        otherOnlineRoom.setStatus("online");

        when(liveRoomMapper.selectList(any()))
                .thenReturn(Collections.singletonList(existingRoom))
                .thenReturn(Collections.singletonList(otherOnlineRoom))
                .thenReturn(Collections.singletonList(existingRoom));

        LiveRoomCreateDTO dto = new LiveRoomCreateDTO();
        dto.setTitle("复用直播间");
        dto.setCategoryId(9L);
        dto.setCoverUrl("https://test-host/cover-2.png");

        LiveRoomVO result = service.createRoom(dto, 42L);

        assertEquals(9001L, result.getRoomId());
        assertEquals("复用直播间", result.getTitle());
        verify(service).updateBatchById(any());
        verify(liveRoomMapper).updateById(any(LiveRoom.class));
        verify(liveTranscodeService).stop(9002L);
        verify(liveTranscodeService).start(
                9001L,
                "rtmp://test-host/live/" + existingRoom.getStreamName(),
                "rtmp://test-host/live/" + existingRoom.getStreamName() + "_480p",
                "rtmp://test-host/live/" + existingRoom.getStreamName() + "_720p");
    }

    @Test
    void createRoom_shouldRejectBlankTitle_andStopBeforePersistence() {
        LiveRoomCreateDTO dto = new LiveRoomCreateDTO();
        dto.setTitle("  ");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRoom(dto, 42L));

        assertEquals("直播间标题不能为空", exception.getMessage());
        verify(liveRoomMapper, never()).selectList(any());
        verify(liveRoomMapper, never()).insert(any(LiveRoom.class));
        verify(liveRoomMapper, never()).updateById(any(LiveRoom.class));
        verify(liveTranscodeService, never()).start(any(), any(), any(), any());
    }

    @Test
    void createRoom_shouldRejectMissingLogin_andStopBeforePersistence() {
        LiveRoomCreateDTO dto = new LiveRoomCreateDTO();
        dto.setTitle("未登录直播间");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRoom(dto, null));

        assertEquals("请先登录后再开始直播", exception.getMessage());
        verify(liveRoomMapper, never()).selectList(any());
        verify(liveRoomMapper, never()).insert(any(LiveRoom.class));
        verify(liveDanmuService, never()).clearRoomDanmu(any());
        verify(roomLikesService, never()).resetLikeCount(any());
        verify(liveTranscodeService, never()).start(any(), any(), any(), any());
    }
}
