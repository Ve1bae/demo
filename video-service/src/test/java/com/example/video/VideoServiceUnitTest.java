package com.example.video;

import com.example.video.client.UserPreferenceClient;
import com.example.video.entity.UserVideo;
import com.example.video.entity.Video;
import com.example.video.mapper.UserVideoMapper;
import com.example.video.mapper.VideoMapper;
import com.example.video.mapper.ViewHistoryMapper;
import com.example.video.service.MinioService;
import com.example.video.service.VideoTranscodeService;
import com.example.video.service.impl.VideoServiceImpl;
import com.example.video.vo.VideoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoServiceUnitTest {
    @Mock private VideoMapper videoMapper;
    @Mock private UserVideoMapper userVideoMapper;
    @Mock private ViewHistoryMapper viewHistoryMapper;
    @Mock private UserPreferenceClient userPreferenceClient;
    @Mock private MinioService minioService;
    @Mock private VideoTranscodeService transcodeService;

    private VideoServiceImpl service() {
        VideoServiceImpl service = new VideoServiceImpl(userVideoMapper, viewHistoryMapper, userPreferenceClient, minioService, transcodeService);
        ReflectionTestUtils.setField(service, "baseMapper", videoMapper);
        return service;
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "demo.mp4", "video/mp4", new byte[]{1, 2, 3});
    }

    private Video ownedVideo() {
        Video video = new Video();
        video.setId(1L);
        video.setUserId(10L);
        video.setStatus("public");
        video.setLikeCount(0);
        return video;
    }

    @Test void uploadRejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> service().upload(file(), "   ", null, null, null, null, 10L, null));
    }

    @Test void uploadRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.mp4", "video/mp4", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service().upload(empty, "标题", null, null, null, null, 10L, null));
    }

    @Test void ownerDeleteReturnsTrue() {
        when(videoMapper.selectById(1L)).thenReturn(ownedVideo());
        when(videoMapper.updateById(any(Video.class))).thenReturn(1);
        assertTrue(service().softDelete(1L, 10L));
    }

    @Test void nonOwnerDeleteReturnsFalse() {
        Video video = ownedVideo();
        video.setUserId(20L);
        when(videoMapper.selectById(1L)).thenReturn(video);
        assertFalse(service().softDelete(1L, 10L));
    }

    @Test void toggleLikeInsertsRelationWhenAbsent() {
        when(videoMapper.selectById(1L)).thenReturn(ownedVideo());
        when(userVideoMapper.selectOne(any())).thenReturn(null);
        var result = service().toggleLike(10L, 1L);
        assertEquals(true, result.get("liked"));
        assertEquals(1, result.get("likeCount"));
        verify(userVideoMapper).insert(any(UserVideo.class));
    }

    @Test void recommendClampsPageSizeToFifty() {
        List<Video> videos = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            Video video = new Video();
            video.setId((long) i);
            video.setStatus("public");
            videos.add(video);
        }
        when(videoMapper.selectList(any())).thenReturn(videos);
        List<VideoVO> result = service().recommend(0, 1000, null, null, null);
        assertEquals(50, result.size());
    }

    @Test void recommendPageBeyondRangeReturnsEmpty() {
        when(videoMapper.selectList(any())).thenReturn(List.of());
        List<VideoVO> result = service().recommend(99, 12, null, null, null);
        assertEquals(0, result.size());
    }
}
