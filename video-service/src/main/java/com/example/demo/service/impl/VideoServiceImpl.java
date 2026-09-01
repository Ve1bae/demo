package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.UserVideo;
import com.example.demo.entity.ViewHistory;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserVideoMapper;
import com.example.demo.mapper.ViewHistoryMapper;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    private final UserVideoMapper userVideoMapper;
    private final ViewHistoryMapper viewHistoryMapper;

    @Override
    public List<Video> getAllVideos() {
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", "public");
        queryWrapper.orderByDesc("created_at");
        List<Video> videos = baseMapper.selectList(queryWrapper);
        videos.forEach(this::decorateVideo);
        return videos;
    }

    @Override
    public Video getVideoById(Long id) {
        Video video = baseMapper.selectById(id);
        if (video != null) {
            decorateVideo(video);
        }
        return video;
    }

    @Override
    public Video getVideoByVideoUrl(String videoUrl) {
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_url", videoUrl);
        Video video = baseMapper.selectOne(queryWrapper);
        if (video != null) {
            decorateVideo(video);
        }
        return video;
    }

    @Override
    public List<Video> getRecommendedFeed(Long userId, Integer page, Integer pageSize, Integer categoryId, String keyword) {
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safePageSize = Math.min(Math.max(pageSize == null ? 12 : pageSize, 1), 50);
        int fromIndex = (safePage - 1) * safePageSize;

        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", "public");
        if (categoryId != null && categoryId > 0) {
            queryWrapper.eq("category_id", categoryId);
        }
        List<Video> videos = baseMapper.selectList(queryWrapper);
        videos.forEach(this::decorateVideo);

        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (!normalizedKeyword.isBlank()) {
            videos = videos.stream()
                    .filter(video -> containsIgnoreCase(video.getTitle(), normalizedKeyword)
                            || containsIgnoreCase(video.getAuthor(), normalizedKeyword)
                            || containsIgnoreCase(video.getDescription(), normalizedKeyword)
                            || containsIgnoreCase(video.getTags(), normalizedKeyword))
                    .collect(Collectors.toList());
        }

        Set<Long> viewedVideoIds = getViewedVideoIds(userId);
        List<Video> sorted = videos.stream()
                .sorted(Comparator
                        .comparingInt((Video video) -> calculateRecommendationScore(video, viewedVideoIds)).reversed()
                        .thenComparing(Video::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        if (fromIndex >= sorted.size()) {
            return List.of();
        }
        return sorted.subList(fromIndex, Math.min(fromIndex + safePageSize, sorted.size()));
    }

    @Override
    public List<Video> getVideosByUserId(Long userId) {
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.ne("status", "deleted");
        queryWrapper.orderByDesc("created_at");
        List<Video> videos = baseMapper.selectList(queryWrapper);
        videos.forEach(this::decorateVideo);
        return videos;
    }

    @Override
    public List<Video> getFavoriteVideosByUserId(Long userId) {
        QueryWrapper<UserVideo> relationWrapper = new QueryWrapper<>();
        relationWrapper.eq("user_id", userId);
        relationWrapper.eq("favorited", true);
        List<Long> videoIds = userVideoMapper.selectList(relationWrapper).stream()
                .map(UserVideo::getVideoId)
                .toList();
        if (videoIds.isEmpty()) {
            return List.of();
        }

        QueryWrapper<Video> videoWrapper = new QueryWrapper<>();
        videoWrapper.in("id", videoIds);
        videoWrapper.eq("status", "public");
        videoWrapper.orderByDesc("created_at");
        List<Video> videos = baseMapper.selectList(videoWrapper);
        videos.forEach(this::decorateVideo);
        return videos;
    }

    @Override
    public long countVideosByUserId(Long userId) {
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.ne("status", "deleted");
        return baseMapper.selectCount(queryWrapper);
    }

    @Override
    public long countFavoriteVideosByUserId(Long userId) {
        return getFavoriteVideosByUserId(userId).size();
    }

    @Override
    @Transactional
    public boolean setVisibility(Long userId, Long videoId, boolean visible) {
        Video video = baseMapper.selectById(videoId);
        if (!isOwnActiveVideo(video, userId)) {
            return false;
        }
        video.setStatus(visible ? "public" : "private");
        return baseMapper.updateById(video) > 0;
    }

    @Override
    @Transactional
    public boolean deleteOwnVideo(Long userId, Long videoId) {
        Video video = baseMapper.selectById(videoId);
        if (!isOwnActiveVideo(video, userId)) {
            return false;
        }
        video.setStatus("deleted");
        return baseMapper.updateById(video) > 0;
    }

    private boolean isOwnActiveVideo(Video video, Long userId) {
        return video != null
                && userId != null
                && userId.equals(video.getUserId())
                && !"deleted".equals(video.getStatus());
    }

    private void decorateVideo(Video video) {
        video.setVideoId(video.getId());
        if (video.getPlayCount() != null) {
            video.setViews(formatPlayCount(video.getPlayCount()));
        }
        setVideoSources(video);
        video.setTagList(parseTags(video.getTags()));
    }

    private void setVideoSources(Video video) {
        Map<String, String> sources = new LinkedHashMap<>();
        if (video.getUrl240p() != null && !video.getUrl240p().isBlank()) {
            sources.put("240P", video.getUrl240p());
        }
        if (video.getUrl360p() != null && !video.getUrl360p().isBlank()) {
            sources.put("360P", video.getUrl360p());
        }
        if (video.getUrl480p() != null && !video.getUrl480p().isBlank()) {
            sources.put("480P", video.getUrl480p());
        }
        if (video.getUrl720p() != null && !video.getUrl720p().isBlank()) {
            sources.put("720P", video.getUrl720p());
        }
        if (video.getUrl1080p() != null && !video.getUrl1080p().isBlank()) {
            sources.put("1080P", video.getUrl1080p());
        }
        if (sources.isEmpty() && video.getPlayUrl() != null && !video.getPlayUrl().isBlank()) {
            sources.put("720P", video.getPlayUrl());
        }
        video.setSources(sources);
        video.setDefaultQuality(video.getDefaultQuality() != null
                ? video.getDefaultQuality()
                : (sources.containsKey("720P") ? "720P" : sources.keySet().stream().findFirst().orElse("720P")));
    }

    @Override
    @Transactional
    public Map<String, Object> toggleLike(Long userId, Long videoId) {
        Map<String, Object> result = new HashMap<>();
        UserVideo userVideo = getOrCreateUserVideo(userId, videoId);
        boolean newLiked = !Boolean.TRUE.equals(userVideo.getLiked());
        userVideo.setLiked(newLiked);
        userVideoMapper.updateById(userVideo);
        userVideoMapper.updateVideoLikeCount(videoId, newLiked ? 1 : -1);

        Video video = baseMapper.selectById(videoId);
        result.put("videoId", videoId);
        result.put("liked", newLiked);
        result.put("likeCount", video != null ? video.getLikeCount() : 0);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> toggleFavorite(Long userId, Long videoId) {
        Map<String, Object> result = new HashMap<>();
        UserVideo userVideo = getOrCreateUserVideo(userId, videoId);
        boolean newFavorited = !Boolean.TRUE.equals(userVideo.getFavorited());
        userVideo.setFavorited(newFavorited);
        userVideoMapper.updateById(userVideo);
        userVideoMapper.updateVideoFavoriteCount(videoId, newFavorited ? 1 : -1);

        Video video = baseMapper.selectById(videoId);
        result.put("videoId", videoId);
        result.put("favorited", newFavorited);
        result.put("favoriteCount", video != null ? video.getFavoriteCount() : 0);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> incrementPlayCount(Long videoId) {
        return incrementPlayCount(videoId, null);
    }

    @Override
    @Transactional
    public Map<String, Object> incrementPlayCount(Long videoId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        userVideoMapper.incrementPlayCount(videoId);
        recordViewHistory(userId, videoId);
        Video video = baseMapper.selectById(videoId);
        result.put("videoId", videoId);
        result.put("playCount", video != null ? video.getPlayCount() : 0);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> touchViewHistory(Long videoId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("videoId", videoId);
        result.put("recorded", recordViewHistory(userId, videoId) != null);
        return result;
    }

    @Override
    public Map<String, Object> getUserVideoStatus(Long userId, Long videoId) {
        Map<String, Object> result = new HashMap<>();
        UserVideo userVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
        result.put("liked", userVideo != null && Boolean.TRUE.equals(userVideo.getLiked()));
        result.put("favorited", userVideo != null && Boolean.TRUE.equals(userVideo.getFavorited()));
        result.put("videoId", videoId);
        return result;
    }

    private UserVideo getOrCreateUserVideo(Long userId, Long videoId) {
        UserVideo userVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
        if (userVideo != null) {
            return userVideo;
        }

        UserVideo newUserVideo = new UserVideo();
        newUserVideo.setUserId(userId);
        newUserVideo.setVideoId(videoId);
        newUserVideo.setLiked(false);
        newUserVideo.setFavorited(false);

        try {
            userVideoMapper.insert(newUserVideo);
            return newUserVideo;
        } catch (DuplicateKeyException ignored) {
            UserVideo existingUserVideo = userVideoMapper.findByUserIdAndVideoId(userId, videoId);
            if (existingUserVideo != null) {
                return existingUserVideo;
            }
            throw ignored;
        }
    }

    private ViewHistory recordViewHistory(Long userId, Long videoId) {
        if (userId == null || videoId == null) {
            return null;
        }
        QueryWrapper<ViewHistory> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("video_id", videoId);
        ViewHistory history = viewHistoryMapper.selectOne(wrapper);
        LocalDateTime now = LocalDateTime.now();
        if (history == null) {
            history = new ViewHistory();
            history.setUserId(userId);
            history.setVideoId(videoId);
            history.setViewCount(1);
            history.setProgressSeconds(0);
            history.setLastViewedAt(now);
            viewHistoryMapper.insert(history);
            return history;
        }
        history.setViewCount((history.getViewCount() == null ? 0 : history.getViewCount()) + 1);
        history.setLastViewedAt(now);
        viewHistoryMapper.updateById(history);
        return history;
    }

    private Set<Long> getViewedVideoIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return viewHistoryMapper.selectList(new QueryWrapper<ViewHistory>().eq("user_id", userId))
                .stream()
                .map(ViewHistory::getVideoId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private int calculateRecommendationScore(Video video, Set<Long> viewedVideoIds) {
        int playCount = video.getPlayCount() == null ? 0 : video.getPlayCount();
        int likeCount = video.getLikeCount() == null ? 0 : video.getLikeCount();
        int favoriteCount = video.getFavoriteCount() == null ? 0 : video.getFavoriteCount();
        int commentCount = video.getCommentCount() == null ? 0 : video.getCommentCount();
        int score = Math.min(playCount, 50000) / 80
                + Math.min(likeCount, 5000) / 4
                + Math.min(favoriteCount, 3000) / 3
                + Math.min(commentCount, 2000) / 4;
        if (video.getId() != null && viewedVideoIds.contains(video.getId())) {
            score -= 20;
        }
        if (video.getCreatedAt() != null && video.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            score += 20;
        }
        return score;
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return List.of(tags.split("[,，\\s]+")).stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .limit(8)
                .toList();
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String formatPlayCount(Integer playCount) {
        if (playCount >= 10000) {
            return String.format("%.1f万", playCount / 10000.0);
        }
        return String.valueOf(playCount);
    }
}
