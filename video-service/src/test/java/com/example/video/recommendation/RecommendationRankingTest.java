package com.example.video.recommendation;

import com.example.video.client.UserPreferenceClient;
import com.example.video.entity.Video;
import com.example.video.mapper.UserVideoMapper;
import com.example.video.mapper.VideoMapper;
import com.example.video.mapper.ViewHistoryMapper;
import com.example.video.model.UserPreference;
import com.example.video.service.MinioService;
import com.example.video.service.VideoTranscodeService;
import com.example.video.service.impl.VideoServiceImpl;
import com.example.video.vo.VideoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationRankingTest {
    @Mock private VideoMapper videoMapper;
    @Mock private UserVideoMapper userVideoMapper;
    @Mock private ViewHistoryMapper viewHistoryMapper;
    @Mock private UserPreferenceClient preferenceClient;
    @Mock private MinioService minioService;
    @Mock private VideoTranscodeService transcodeService;

    private VideoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VideoServiceImpl(userVideoMapper, viewHistoryMapper, preferenceClient,
                minioService, transcodeService);
        ReflectionTestUtils.setField(service, "baseMapper", videoMapper);
    }

    @Test
    void ranksPublicVideosByCounters() {
        Video low = video(1L, "低热度", 10L, 10, 0, 0, 0, LocalDateTime.now());
        Video high = video(2L, "高热度", 10L, 1000, 20, 10, 8, LocalDateTime.now().minusDays(1));
        when(videoMapper.selectList(any())).thenReturn(List.of(low, high));

        assertEquals(List.of(2L, 1L), ids(service.recommend(1, 12, null, null, null)));
    }

    @Test
    void followedAuthorGetsPriorityAndFollowingState() {
        Video followed = video(1L, "关注作者作品", 10L, 0, 0, 0, 0, LocalDateTime.now().minusDays(30));
        Video popular = video(2L, "热门作品", 11L, 500, 0, 0, 0, LocalDateTime.now().minusDays(30));
        when(videoMapper.selectList(any())).thenReturn(List.of(popular, followed));
        when(preferenceClient.getPreference(42L))
                .thenReturn(new UserPreference(Set.of(10L), Map.of(), Set.of()));
        when(userVideoMapper.selectOne(any())).thenReturn(null);
        when(viewHistoryMapper.selectList(any())).thenReturn(List.of());

        List<VideoVO> result = service.recommend(1, 12, null, null, 42L);

        assertEquals(List.of(1L, 2L), ids(result));
        assertTrue(result.get(0).getAuthorInfo().getFollowing());
    }

    @Test
    void interestTagRaisesPriority() {
        Video music = video(1L, "音乐", 1L, 0, 0, 0, 0, LocalDateTime.now().minusDays(1));
        music.setTags("音乐");
        Video news = video(2L, "新闻", 1L, 0, 0, 0, 0, LocalDateTime.now().minusDays(1));
        when(videoMapper.selectList(any())).thenReturn(List.of(news, music));
        when(preferenceClient.getPreference(42L))
                .thenReturn(new UserPreference(Set.of(), Map.of("音乐", 80), Set.of()));
        when(userVideoMapper.selectOne(any())).thenReturn(null);
        when(viewHistoryMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(1L, 2L), ids(service.recommend(1, 12, null, null, 42L)));
    }

    @Test
    void viewedVideoGetsPenaltyFromRemotePreferenceAndLocalHistory() {
        Video viewed = video(1L, "已观看", 1L, 100, 0, 0, 0, LocalDateTime.now());
        Video unseen = video(2L, "未观看", 1L, 100, 0, 0, 0, LocalDateTime.now());
        when(videoMapper.selectList(any())).thenReturn(List.of(viewed, unseen));
        when(preferenceClient.getPreference(42L))
                .thenReturn(new UserPreference(Set.of(), Map.of(), Set.of(1L)));
        when(userVideoMapper.selectOne(any())).thenReturn(null);
        when(viewHistoryMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(2L, 1L), ids(service.recommend(1, 12, null, null, 42L)));
    }

    @Test
    void preferenceFailureFallsBackToPopularity() {
        Video popular = video(1L, "热门", 11L, 500, 0, 0, 0, LocalDateTime.now().minusDays(1));
        Video followedOnly = video(2L, "关注作者", 10L, 0, 0, 0, 0, LocalDateTime.now());
        when(videoMapper.selectList(any())).thenReturn(List.of(followedOnly, popular));
        when(preferenceClient.getPreference(42L)).thenThrow(new IllegalStateException("user-service down"));
        when(viewHistoryMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(1L, 2L), ids(service.recommend(1, 12, null, null, 42L)));
    }

    @Test
    void clampsPageAndPageSizeAndReturnsEmptyWhenOutOfRange() {
        List<Video> videos = new ArrayList<>();
        for (long id = 1; id <= 55; id++) videos.add(video(id, "视频" + id, 1L, 0, 0, 0, 0,
                LocalDateTime.now().minusSeconds(id)));
        when(videoMapper.selectList(any())).thenReturn(videos);

        assertEquals(50, service.recommend(0, 1000, null, null, null).size());
        assertTrue(service.recommend(Integer.MAX_VALUE, 1, null, null, null).isEmpty());
    }

    @Test
    void normalizesTagsAndSparseVideoSafely() {
        Video sparse = video(1L, "稀疏", 1L, 0, 0, 0, 0, null);
        sparse.setTags("a,b a，c d e f g h i j");
        when(videoMapper.selectList(any())).thenReturn(List.of(sparse));

        VideoVO result = service.recommend(1, 12, null, null, null).get(0);

        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g", "h"), result.getTagList());
        assertTrue(result.getSources().isEmpty());
    }

    @Test
    void tieBreaksByRecentTimeThenId() {
        Video old = video(1L, "旧", 1L, 0, 0, 0, 0, LocalDateTime.now().minusDays(1));
        Video recent = video(2L, "新", 1L, 0, 0, 0, 0, LocalDateTime.now());
        when(videoMapper.selectList(any())).thenReturn(List.of(old, recent));

        assertEquals(List.of(2L, 1L), ids(service.recommend(1, 12, null, null, null)));
    }

    private Video video(Long id, String title, Long userId, int play, int likes,
                        int favorites, int comments, LocalDateTime createdAt) {
        Video video = new Video();
        video.setId(id);
        video.setTitle(title);
        video.setUserId(userId);
        video.setStatus("public");
        video.setPlayCount(play);
        video.setLikeCount(likes);
        video.setFavoriteCount(favorites);
        video.setCommentCount(comments);
        video.setCreatedAt(createdAt);
        return video;
    }

    private List<Long> ids(List<VideoVO> videos) {
        return videos.stream().map(VideoVO::getId).toList();
    }
}



