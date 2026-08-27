package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.entity.User;
import com.example.demo.entity.UserFollow;
import com.example.demo.entity.UserInterest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class VideoServiceImplTest {

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

        User author = new User();
        author.setId(10L);
        author.setNickname("作者甲");
        lenient().when(userMapper.selectById(10L)).thenReturn(author);
        lenient().when(userFollowMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        lenient().when(viewHistoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        lenient().when(userInterestMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
    }

    @Test
    void returnsPublicVideosInRecommendationOrder() {
        Video popular = video(1L, "热门视频", 10L, 1000, 20, 10, 8, LocalDateTime.now().minusDays(2));
        Video recent = video(2L, "新视频", 10L, 1, 0, 0, 0, LocalDateTime.now().minusHours(1));
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(recent, popular));

        List<Video> result = videoService.getRecommendedFeed(null, 1, 12, 0, null);

        assertEquals(List.of(popular, recent), result);
        verify(videoMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void filtersByKeywordAcrossVideoFields() {
        Video matching = video(1L, "校园音乐会", 10L, 1, 0, 0, 0, LocalDateTime.now());
        matching.setTags("音乐 校园");
        Video other = video(2L, "旅行记录", 10L, 100, 0, 0, 0, LocalDateTime.now());
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(matching, other));

        List<Video> result = videoService.getRecommendedFeed(null, 1, 12, 0, " 校园 ");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void appliesPaginationAndClampsInvalidValues() {
        Video first = video(1L, "第一条", 10L, 100, 0, 0, 0, LocalDateTime.now());
        Video second = video(2L, "第二条", 10L, 90, 0, 0, 0, LocalDateTime.now().minusMinutes(1));
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(first, second));

        List<Video> page = videoService.getRecommendedFeed(null, 0, 0, 0, null);

        assertEquals(1, page.size());
        assertEquals(1L, page.get(0).getId());
    }

    @Test
    void followedAuthorGetsRecommendationPriorityForLoggedInUser() {
        User otherAuthor = new User();
        otherAuthor.setId(11L);
        otherAuthor.setNickname("作者乙");
        lenient().when(userMapper.selectById(11L)).thenReturn(otherAuthor);

        Video followed = video(1L, "普通视频", 10L, 0, 0, 0, 0, LocalDateTime.now().minusDays(30));
        Video normal = video(2L, "热门视频", 11L, 200, 0, 0, 0, LocalDateTime.now().minusDays(30));
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(normal, followed));

        UserFollow follow = new UserFollow();
        follow.setFollowUserId(10L);
        when(userFollowMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(follow));

        List<Video> result = videoService.getRecommendedFeed(99L, 1, 12, 0, null);

        assertEquals(1L, result.get(0).getId());
        assertTrue(Boolean.TRUE.equals(result.get(0).getAuthorInfo().getFollowing()));
    }

    @Test
    void returnsEmptyListWhenPageStartsAfterResults() {
        Video only = video(1L, "唯一视频", 10L, 1, 0, 0, 0, LocalDateTime.now());
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(only));

        List<Video> result = videoService.getRecommendedFeed(null, 2, 1, 0, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void guestDoesNotReadPersonalizedRecommendationData() {
        Video publicVideo = video(1L, "公开视频", 10L, 10, 0, 0, 0, LocalDateTime.now());
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(publicVideo));

        videoService.getRecommendedFeed(null, 1, 12, 0, null);

        verify(userFollowMapper, never()).selectList(any(QueryWrapper.class));
        verify(viewHistoryMapper, never()).selectList(any(QueryWrapper.class));
        verify(userInterestMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void loggedInUserLoadsAllPersonalizationSources() {
        Video publicVideo = video(1L, "个性化视频", 10L, 10, 0, 0, 0, LocalDateTime.now());
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(publicVideo));

        videoService.getRecommendedFeed(99L, 1, 12, 0, null);

        verify(videoMapper).selectList(any(QueryWrapper.class));
        verify(userMapper).selectById(10L);
        verify(userFollowMapper).selectList(any(QueryWrapper.class));
        verify(viewHistoryMapper).selectList(any(QueryWrapper.class));
        verify(userInterestMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void matchingInterestTagIncreasesRecommendationPriority() {
        User otherAuthor = new User();
        otherAuthor.setId(11L);
        otherAuthor.setNickname("作者乙");
        lenient().when(userMapper.selectById(11L)).thenReturn(otherAuthor);

        Video music = video(1L, "音乐视频", 10L, 0, 0, 0, 0, LocalDateTime.now().minusDays(30));
        music.setTags("音乐");
        Video travel = video(2L, "旅行视频", 11L, 0, 0, 0, 0, LocalDateTime.now().minusDays(30));
        travel.setTags("旅行");
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(travel, music));

        UserInterest interest = new UserInterest();
        interest.setTag("音乐");
        interest.setScore(20);
        when(userInterestMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(interest));

        List<Video> result = videoService.getRecommendedFeed(99L, 1, 12, 0, null);

        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void viewedVideoReceivesRecommendationPenalty() {
        Video viewed = video(1L, "已看视频", 10L, 100, 0, 0, 0, LocalDateTime.now().minusDays(30));
        Video unseen = video(2L, "未看视频", 10L, 100, 0, 0, 0, LocalDateTime.now().minusDays(30));
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(viewed, unseen));

        com.example.demo.entity.ViewHistory history = new com.example.demo.entity.ViewHistory();
        history.setVideoId(1L);
        when(viewHistoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(history));

        List<Video> result = videoService.getRecommendedFeed(99L, 1, 12, 0, null);

        assertEquals(2L, result.get(0).getId());
    }

    @Test
    void buildsPublicAndCategoryFilterForCategoryRequest() {
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        videoService.getRecommendedFeed(null, 1, 12, 7, null);

        ArgumentCaptor<QueryWrapper<Video>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(videoMapper).selectList(captor.capture());
        QueryWrapper<Video> query = captor.getValue();
        // MyBatis-Plus may render bound values differently across versions;
        // assert the generated condition names and the bound values without
        // depending on a concrete value type.
        assertTrue(query.getSqlSegment().contains("status"));
        assertTrue(query.getSqlSegment().contains("category_id"));
        assertTrue(query.getParamNameValuePairs().values().stream()
                .map(String::valueOf)
                .anyMatch("public"::equals));
        assertTrue(query.getParamNameValuePairs().values().stream()
                .map(String::valueOf)
                .anyMatch("7"::equals));
    }

    @Test
    void recentVideoGetsFreshnessBonusWhenOtherScoresTie() {
        Video recent = video(1L, "近期视频", 10L, 0, 0, 0, 0, LocalDateTime.now().minusDays(2));
        Video old = video(2L, "旧视频", 10L, 0, 0, 0, 0, LocalDateTime.now().minusDays(30));
        when(videoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(old, recent));

        List<Video> result = videoService.getRecommendedFeed(null, 1, 12, 0, null);

        assertEquals(1L, result.get(0).getId());
    }

    private Video video(Long id, String title, Long userId, int playCount, int likeCount,
                        int favoriteCount, int commentCount, LocalDateTime createdAt) {
        Video video = new Video();
        video.setId(id);
        video.setTitle(title);
        video.setUserId(userId);
        video.setStatus("public");
        video.setPlayCount(playCount);
        video.setLikeCount(likeCount);
        video.setFavoriteCount(favoriteCount);
        video.setCommentCount(commentCount);
        video.setCreatedAt(createdAt);
        video.setPlayUrl("http://localhost/video-" + id + ".mp4");
        return video;
    }
}
