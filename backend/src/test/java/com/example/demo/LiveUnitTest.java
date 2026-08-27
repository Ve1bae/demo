package com.example.demo;

import com.example.demo.entity.LiveDanmu;
import com.example.demo.entity.RoomLikes;
import com.example.demo.mapper.LiveDanmuMapper;
import com.example.demo.mapper.RoomLikesMapper;
import com.example.demo.service.LiveDanmuService;
import com.example.demo.service.RoomLikesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveUnitTest {

    @Mock
    private LiveDanmuMapper liveDanmuMapper;

    @Mock
    private RoomLikesMapper roomLikesMapper;

    private LiveDanmuService liveDanmuService;

    private RoomLikesService roomLikesService;

    @BeforeEach
    void setUp() {
        liveDanmuService = new LiveDanmuService();
        ReflectionTestUtils.setField(liveDanmuService, "baseMapper", liveDanmuMapper);

        roomLikesService = new RoomLikesService();
        ReflectionTestUtils.setField(roomLikesService, "baseMapper", roomLikesMapper);
    }

    @Test
    void blankDanmuIsRejectedBeforeDatabaseWrite() {
        assertThrows(IllegalArgumentException.class, () ->
                liveDanmuService.saveDanmu(1L, 1L, "用户", "   ", "#ffffff")
        );
        verify(liveDanmuMapper, never()).insert(any(LiveDanmu.class));
    }

    @Test
    void overlongDanmuIsRejectedBeforeDatabaseWrite() {
        String overlong = "x".repeat(300);
        assertThrows(IllegalArgumentException.class, () ->
                liveDanmuService.saveDanmu(1L, 1L, "用户", overlong, "#ffffff")
        );
        verify(liveDanmuMapper, never()).insert(any(LiveDanmu.class));
    }

    @Test
    void validDanmuIsTrimmedAndUsesDefaultColor() {
        LiveDanmu result = liveDanmuService.saveDanmu(
                1L,
                1L,
                "用户",
                "  主播晚上好  ",
                null
        );

        assertNotNull(result);
        assertEquals("主播晚上好", result.getContent());
        assertEquals("#ffffff", result.getColor());
        assertEquals(1L, result.getRoomId());
        verify(liveDanmuMapper).insert(any(LiveDanmu.class));
    }

    @Test
    void addLikeUpsertsAndReadsLatestCount() {
        RoomLikes likes = new RoomLikes();
        likes.setRoomId(1L);
        likes.setLikeCount(5L);
        when(roomLikesMapper.selectById(1L)).thenReturn(likes);

        long result = roomLikesService.addLike(1L);

        assertEquals(5L, result);
        verify(roomLikesMapper).upsertIncrement(1L);
        verify(roomLikesMapper).selectById(1L);
    }

    @Test
    void missingLikeRecordReturnsZero() {
        when(roomLikesMapper.selectById(1L)).thenReturn(null);

        assertEquals(0L, roomLikesService.getLikeCount(1L));
    }
}
