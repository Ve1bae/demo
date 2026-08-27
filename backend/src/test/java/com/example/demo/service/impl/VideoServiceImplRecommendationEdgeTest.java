package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserFollowMapper;
import com.example.demo.mapper.UserInterestMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.UserVideoMapper;
import com.example.demo.mapper.ViewHistoryMapper;
import com.example.demo.mapper.VideoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 推荐算法边界测试，不访问数据库、MinIO 或其它外部服务。
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class VideoServiceImplRecommendationEdgeTest {

    @Mock
    private VideoMapper videoMapper;

    @Mock
    private UserVideoMapper userVideoMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserFollowMapper userFollowMapper;

    @Mock
    private UserInterestMapper userInterestMapper;

    @Mock
    private ViewHistoryMapper viewHistoryMapper;

    private VideoServiceImpl videoService;

    @BeforeEach
    void setUp() {
        videoService = new VideoServiceImpl(userVideoMapper, userMapper, userFollowMapper,
                userInterestMapper, viewHistoryMapper);
        ReflectionTestUtils.setField(videoService, "baseMapper", videoMapper);
        lenient().when(userFollowMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        lenient().when(viewHistoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        lenient().when(userInterestMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        lenient().when(userFollowMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
    }

    @Test
    void handlesNullVideoFieldsAndMissingAuthorWithoutThrowing() {
        Video sparse = new Video();
        sparse.setId(101L);
        sparse.setStatus("public");
        sparse.setUserId(404L);
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(sparse));
        when(userMapper.selectById(404L)).thenReturn(null);

        List<Video> result = videoService.getRecommendedFeed(null, null, null, null, null);

        assertEquals(1, result.size());
        Video actual = result.get(0);
        assertEquals(101L, actual.getVideoId());
        assertEquals(List.of(), actual.getTagList());
        assertNotNull(actual.getSources());
        assertTrue(actual.getSources().isEmpty());
        assertEquals("720P", actual.getDefaultQuality());
        assertNull(actual.getAuthorInfo());
    }

    @Test
    void removesEmptyAndDuplicateTagsAndLimitsToEightTags() {
        Video tagged = video(102L, "标签测试", null, LocalDateTime.now());
        tagged.setTags("  alpha,,beta alpha，gamma\tdelta epsilon zeta eta theta iota kappa  ");
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(tagged));

        List<Video> result = videoService.getRecommendedFeed(null, 1, 12, 0, null);

        assertEquals(1, result.size());
        assertEquals(List.of("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta"),
                result.get(0).getTagList());
    }

    @Test
    void clampsPageSizeToFiftyAndAcceptsNegativeCategoryAsAllCategories() {
        List<Video> videos = new ArrayList<>();
        for (long id = 1; id <= 55; id++) {
            videos.add(video(id, "视频" + id, null, LocalDateTime.now().minusSeconds(id)));
        }
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(videos);

        List<Video> result = videoService.getRecommendedFeed(null, 1, 100, -7, null);

        assertEquals(50, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void returnsEmptyForLargestPageNumberWithoutIntegerOverflow() {
        when(videoMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(video(103L, "单条视频", null, LocalDateTime.now())));

        List<Video> result = videoService.getRecommendedFeed(null, Integer.MAX_VALUE, 1, 0, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void putsNonNullCreationTimeBeforeNullWhenScoresTie() {
        Video noTime = video(104L, "无时间", null, null);
        Video withTime = video(105L, "有时间", null, LocalDateTime.now().minusDays(1));
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(noTime, withTime));

        List<Video> result = videoService.getRecommendedFeed(null, 1, 12, 0, null);

        assertEquals(List.of(105L, 104L), result.stream().map(Video::getId).toList());
    }

    @Test
    void treatsWhitespaceOnlyKeywordAsUnfiltered() {
        Video first = video(106L, "第一个", null, LocalDateTime.now());
        Video second = video(107L, "第二个", null, LocalDateTime.now().minusMinutes(1));
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(first, second));

        List<Video> result = videoService.getRecommendedFeed(null, 1, 12, 0, "   ");

        assertEquals(2, result.size());
    }

    @Test
    void handlesMaximumCounterValuesWithoutScoreOverflow() {
        Video highCounters = video(108L, "高计数", null, LocalDateTime.now().minusDays(30));
        highCounters.setPlayCount(Integer.MAX_VALUE);
        highCounters.setLikeCount(Integer.MAX_VALUE);
        highCounters.setFavoriteCount(Integer.MAX_VALUE);
        highCounters.setCommentCount(Integer.MAX_VALUE);
        Video lowCounters = video(109L, "低计数", null, LocalDateTime.now().minusDays(30));
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(lowCounters, highCounters));

        List<Video> result = videoService.getRecommendedFeed(null, 1, 12, 0, null);

        assertEquals(108L, result.get(0).getId());
    }

    private Video video(Long id, String title, Long userId, LocalDateTime createdAt) {
        Video video = new Video();
        video.setId(id);
        video.setTitle(title);
        video.setUserId(userId);
        video.setStatus("public");
        video.setCreatedAt(createdAt);
        video.setPlayCount(0);
        video.setLikeCount(0);
        video.setFavoriteCount(0);
        video.setCommentCount(0);
        video.setPlayUrl("https://example.test/videos/" + id + ".mp4");
        return video;
    }
}
