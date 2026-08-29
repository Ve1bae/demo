package com.example.video.service;

import com.example.video.client.UserPreferenceClient;
import com.example.video.model.UserPreference;
import com.example.video.model.Video;
import com.example.video.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class RecommendationServiceTest {
    @Mock
    private VideoRepository videoRepository;
    @Mock
    private UserPreferenceClient preferenceClient;

    private RecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecommendationServiceImpl(videoRepository, preferenceClient);
    }

    @Test
    @DisplayName("UNIT-TC-03-01 公色视频按热度排序")
    void ranksPublicVideosByCounters() {
        Video low = video(1L, "低热度", 10L, 10, 0, 0, 0, LocalDateTime.now());
        Video high = video(2L, "高热度", 10L, 1000, 20, 10, 8, LocalDateTime.now().minusDays(1));
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(low, high));

        assertEquals(List.of(2L, 1L), ids(service.recommend(null, 1, 12, 0, null)));
    }

    @Test
    @DisplayName("UNIT-TC-03-02 关键词匹配标题描述作者和标签")
    void filtersAllSearchableFields() {
        Video match = video(1L, "普通标题", 10L, 1, 0, 0, 0, LocalDateTime.now());
        match.setDescription("校园活动");
        match.setTags("音乐 校园");
        match.setAuthor("作者");
        Video other = video(2L, "旅行", 11L, 100, 0, 0, 0, LocalDateTime.now());
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(other, match));

        assertEquals(List.of(1L), ids(service.recommend(null, 1, 12, 0, " 校园 ")));
    }

    @Test
    @DisplayName("UNIT-TC-03-03 分页参数安全修正")
    void clampsInvalidPagingValues() {
        List<Video> videos = new ArrayList<>();
        videos.add(video(1L, "第一条", 1L, 100, 0, 0, 0, LocalDateTime.now()));
        videos.add(video(2L, "第二条", 1L, 90, 0, 0, 0, LocalDateTime.now().minusMinutes(1)));
        when(videoRepository.findPublicVideos(0)).thenReturn(videos);

        assertEquals(List.of(1L), ids(service.recommend(null, 0, 0, 0, null)));
    }

    @Test
    @DisplayName("UNIT-TC-03-04 关注作者获得加分")
    void followedAuthorGetsPriority() {
        Video followed = video(1L, "关注作者作品", 10L, 0, 0, 0, 0, LocalDateTime.now().minusDays(30));
        Video popular = video(2L, "热门作品", 11L, 500, 0, 0, 0, LocalDateTime.now().minusDays(30));
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(popular, followed));
        when(preferenceClient.getPreference(42L)).thenReturn(
                new UserPreference(Set.of(10L), Map.of(), Set.of()));

        List<Video> result = service.recommend(42L, 1, 12, 0, null);

        assertEquals(1L, result.get(0).getId());
        assertTrue(result.get(0).getAuthorInfo().getFollowing());
    }

    @Test
    @DisplayName("UNIT-TC-03-05 页码越界返回空列表")
    void returnsEmptyForOutOfRangePage() {
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(video(1L, "唯一", 1L, 1, 0, 0, 0, LocalDateTime.now())));
        assertTrue(service.recommend(null, 2, 1, 0, null).isEmpty());
    }

    @Test
    @DisplayName("UNIT-TC-03-06 游客不读取个性化客户端")
    void guestSkipsPreferenceLookup() {
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of());
        service.recommend(null, 1, 12, 0, null);
        verify(preferenceClient, never()).getPreference(42L);
    }

    @Test
    @DisplayName("UNIT-TC-03-07 登录用户读取偏好")
    void loggedInUserReadsPreferenceClient() {
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of());
        service.recommend(42L, 1, 12, 0, null);
        verify(preferenceClient).getPreference(42L);
    }

    @Test
    @DisplayName("UNIT-TC-03-08 兴趣标签提高排序")
    void interestTagRaisesPriority() {
        Video music = video(1L, "音乐", 1L, 0, 0, 0, 0, LocalDateTime.now().minusDays(1));
        music.setTags("音乐");
        Video news = video(2L, "新闻", 1L, 0, 0, 0, 0, LocalDateTime.now().minusDays(1));
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(news, music));
        when(preferenceClient.getPreference(42L)).thenReturn(new UserPreference(Set.of(), Map.of("音乐", 80), Set.of()));
        assertEquals(List.of(1L, 2L), ids(service.recommend(42L, 1, 12, 0, null)));
    }

    @Test
    @DisplayName("UNIT-TC-03-09 已观看视频降权")
    void viewedVideoGetsPenalty() {
        Video viewed = video(1L, "已观看", 1L, 100, 0, 0, 0, LocalDateTime.now());
        Video unseen = video(2L, "未观看", 1L, 100, 0, 0, 0, LocalDateTime.now());
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(viewed, unseen));
        when(preferenceClient.getPreference(42L)).thenReturn(new UserPreference(Set.of(), Map.of(), Set.of(1L)));
        assertEquals(2L, service.recommend(42L, 1, 12, 0, null).get(0).getId());
    }

    @Test
    @DisplayName("UNIT-TC-03-10 分类参数交给仓储层")
    void passesCategoryToRepository() {
        when(videoRepository.findPublicVideos(7)).thenReturn(List.of());
        service.recommend(null, 1, 12, 7, null);
        verify(videoRepository).findPublicVideos(7);
    }

    @Test
    @DisplayName("UNIT-TC-03-11 相同分数按时间排序")
    void sortsByRecentTimeWhenScoresTie() {
        Video old = video(1L, "旧", 1L, 0, 0, 0, 0, LocalDateTime.now().minusDays(2));
        Video recent = video(2L, "新", 1L, 0, 0, 0, 0, LocalDateTime.now());
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(old, recent));
        assertEquals(List.of(2L, 1L), ids(service.recommend(null, 1, 12, 0, null)));
    }

    @Test
    @DisplayName("UNIT-TC-03-12 空字段安全返回")
    void handlesSparseVideo() {
        Video sparse = new Video();
        sparse.setId(1L);
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(sparse));
        Video result = service.recommend(null, 1, 12, 0, null).get(0);
        assertEquals(1L, result.getVideoId());
        assertNotNull(result.getSources());
        assertTrue(result.getSources().isEmpty());
        assertEquals(List.of(), result.getTagList());
    }

    @Test
    @DisplayName("UNIT-TC-03-13 标签去重并限制八项")
    void normalizesTags() {
        Video video = video(1L, "标签", 1L, 0, 0, 0, 0, LocalDateTime.now());
        video.setTags("a,b a，c d e f g h i j");
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(video));
        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g", "h"),
                service.recommend(null, 1, 12, 0, null).get(0).getTagList());
    }

    @Test
    @DisplayName("UNIT-TC-03-14 每页最多五十条")
    void capsPageSize() {
        List<Video> videos = new ArrayList<>();
        for (long id = 1; id <= 55; id++) {
            videos.add(video(id, "视频" + id, 1L, 0, 0, 0, 0, LocalDateTime.now().minusSeconds(id)));
        }
        when(videoRepository.findPublicVideos(-1)).thenReturn(videos);
        assertEquals(50, service.recommend(null, 1, 100, -1, null).size());
    }

    @Test
    @DisplayName("UNIT-TC-03-15 最大页码不溢出")
    void handlesLargestPageNumber() {
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(video(1L, "视频", 1L, 0, 0, 0, 0, LocalDateTime.now())));
        assertTrue(service.recommend(null, Integer.MAX_VALUE, 1, 0, null).isEmpty());
    }

    @Test
    @DisplayName("UNIT-TC-03-16 空创建时间排序稳定")
    void putsNonNullTimeFirst() {
        Video noTime = video(1L, "无时间", 1L, 0, 0, 0, 0, null);
        Video withTime = video(2L, "有时间", 1L, 0, 0, 0, 0, LocalDateTime.now());
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(noTime, withTime));
        assertEquals(List.of(2L, 1L), ids(service.recommend(null, 1, 12, 0, null)));
    }

    @Test
    @DisplayName("UNIT-TC-03-17 空白关键词不筛选")
    void treatsBlankKeywordAsNoFilter() {
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(
                video(1L, "一", 1L, 0, 0, 0, 0, LocalDateTime.now()),
                video(2L, "二", 1L, 0, 0, 0, 0, LocalDateTime.now().minusMinutes(1))));
        assertEquals(2, service.recommend(null, 1, 12, 0, "  ").size());
    }

    @Test
    @DisplayName("UNIT-TC-03-18 最大计数不溢出")
    void handlesMaximumCounters() {
        Video high = video(1L, "高计数", 1L, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, LocalDateTime.now());
        Video low = video(2L, "低计数", 1L, 0, 0, 0, 0, LocalDateTime.now());
        when(videoRepository.findPublicVideos(0)).thenReturn(List.of(low, high));
        assertEquals(1L, service.recommend(null, 1, 12, 0, null).get(0).getId());
    }

    private List<Long> ids(List<Video> videos) {
        return videos.stream().map(Video::getId).toList();
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
        video.setPlayUrl("https://example.test/" + id + ".mp4");
        return video;
    }
}
