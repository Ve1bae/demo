package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Tag;
import com.example.demo.entity.User;
import com.example.demo.entity.UserVideo;
import com.example.demo.entity.ViewHistory;
import com.example.demo.entity.Video;
import com.example.demo.entity.VideoTag;
import com.example.demo.entity.Danmaku;
import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentLike;
import com.example.demo.mapper.DanmakuMapper;
import com.example.demo.mapper.CommentMapper;
import com.example.demo.mapper.CommentLikeMapper;
import com.example.demo.mapper.TagMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.UserVideoMapper;
import com.example.demo.mapper.ViewHistoryMapper;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.mapper.VideoTagMapper;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final TagMapper tagMapper;
    private final VideoTagMapper videoTagMapper;
    private final UserMapper userMapper;
    private final DanmakuMapper danmakuMapper;
    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;

    @Override
    public List<Video> getAllVideos() {
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
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

        List<Video> candidates = baseMapper.selectList(new QueryWrapper<Video>().orderByDesc("created_at"));
        candidates.forEach(this::decorateVideo);

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        if (StringUtils.hasText(normalizedKeyword)) {
            candidates = candidates.stream()
                    .filter(video -> matchesKeyword(video, normalizedKeyword))
                    .collect(Collectors.toList());
        }

        Map<Long, Integer> preferredTagWeights = buildPreferredTagWeights(userId);
        Set<Long> interactedVideoIds = getInteractedVideoIds(userId);

        List<Video> sorted = candidates.stream()
                .sorted(
                        Comparator.comparingInt((Video video) -> calculateRecommendationScore(video, preferredTagWeights, interactedVideoIds)).reversed()
                                .thenComparing((Video video) -> video.getPlayCount() == null ? 0 : video.getPlayCount(), Comparator.reverseOrder())
                                .thenComparing((Video video) -> video.getLikeCount() == null ? 0 : video.getLikeCount(), Comparator.reverseOrder())
                                .thenComparing((Video video) -> video.getFavoriteCount() == null ? 0 : video.getFavoriteCount(), Comparator.reverseOrder())
                                .thenComparing(Video::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                )
                .collect(Collectors.toList());

        if (fromIndex >= sorted.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + safePageSize, sorted.size());
        return sorted.subList(fromIndex, toIndex);
    }

    @Override
    public List<Video> getRecommendedVideos(Long currentVideoId, Long userId, int limit) {
        int safeLimit = Math.max(limit, 1);
        Video currentVideo = currentVideoId == null ? null : baseMapper.selectById(currentVideoId);
        Set<Long> currentTagIds = currentVideo == null ? Set.of() : getTagIdsByVideoId(currentVideo.getId());

        List<Video> candidates = baseMapper.selectList(new QueryWrapper<Video>().orderByDesc("created_at"));
        Map<Long, Integer> sharedTagCount = new HashMap<>();

        for (Video candidate : candidates) {
            if (currentVideoId != null && currentVideoId.equals(candidate.getId())) {
                continue;
            }
            Set<Long> candidateTagIds = getTagIdsByVideoId(candidate.getId());
            int overlap = 0;
            for (Long tagId : candidateTagIds) {
                if (currentTagIds.contains(tagId)) {
                    overlap++;
                }
            }
            sharedTagCount.put(candidate.getId(), overlap);
        }

        return candidates.stream()
                .filter(video -> currentVideoId == null || !currentVideoId.equals(video.getId()))
                .sorted(
                        Comparator.comparingInt((Video video) -> sharedTagCount.getOrDefault(video.getId(), 0)).reversed()
                                .thenComparing((Video video) -> video.getPlayCount() == null ? 0 : video.getPlayCount(), Comparator.reverseOrder())
                                .thenComparing((Video video) -> video.getLikeCount() == null ? 0 : video.getLikeCount(), Comparator.reverseOrder())
                                .thenComparing(Video::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                )
                .limit(safeLimit)
                .peek(this::decorateVideo)
                .collect(Collectors.toList());
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
    public Map<String, Object> incrementPlayCount(Long videoId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        userVideoMapper.incrementPlayCount(videoId);
        Video video = baseMapper.selectById(videoId);
        result.put("videoId", videoId);
        result.put("playCount", video != null ? video.getPlayCount() : 0);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> touchViewHistory(Long videoId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        recordViewHistory(userId, videoId, true);
        result.put("videoId", videoId);
        result.put("recorded", userId != null);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> updateViewHistoryProgress(Long videoId, Long userId, Integer progressSeconds) {
        Map<String, Object> result = new HashMap<>();
        int normalizedProgress = Math.max(progressSeconds == null ? 0 : progressSeconds, 0);
        ViewHistory history = recordViewHistory(userId, videoId, false);
        if (history != null) {
            history.setProgressSeconds(normalizedProgress);
            history.setLastViewedAt(LocalDateTime.now());
            viewHistoryMapper.updateById(history);
        }
        result.put("videoId", videoId);
        result.put("recorded", userId != null);
        result.put("progressSeconds", normalizedProgress);
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

    @Override
    @Transactional
    public boolean deleteOwnVideo(Long userId, Long videoId) {
        if (userId == null || videoId == null) {
            return false;
        }

        Video video = baseMapper.selectById(videoId);
        if (video == null || !isVideoAuthor(video, userId)) {
            return false;
        }

        QueryWrapper<Comment> commentQuery = new QueryWrapper<>();
        commentQuery.eq("video_id", videoId);
        List<Comment> comments = commentMapper.selectList(commentQuery);
        if (!comments.isEmpty()) {
            List<Long> commentIds = comments.stream().map(Comment::getId).toList();
            QueryWrapper<CommentLike> likeQuery = new QueryWrapper<>();
            likeQuery.in("comment_id", commentIds);
            commentLikeMapper.delete(likeQuery);
            commentMapper.delete(commentQuery);
        }

        userVideoMapper.delete(new QueryWrapper<UserVideo>().eq("video_id", videoId));
        viewHistoryMapper.delete(new QueryWrapper<ViewHistory>().eq("video_id", videoId));
        videoTagMapper.delete(new QueryWrapper<VideoTag>().eq("video_id", videoId));
        if (StringUtils.hasText(video.getVideoUrl())) {
            danmakuMapper.delete(new QueryWrapper<Danmaku>().eq("video_url", video.getVideoUrl()));
        }
        return removeById(videoId);
    }

    @Transactional
    public void bindAutoTags(Video video) {
        if (video == null || video.getId() == null) {
            return;
        }

        QueryWrapper<VideoTag> cleanupWrapper = new QueryWrapper<>();
        cleanupWrapper.eq("video_id", video.getId());
        videoTagMapper.delete(cleanupWrapper);

        List<String> generatedNames = generateTagNames(video);
        for (String name : generatedNames) {
            Tag tag = findOrCreateTag(name);
            VideoTag videoTag = new VideoTag();
            videoTag.setVideoId(video.getId());
            videoTag.setTagId(tag.getId());
            videoTagMapper.insert(videoTag);
        }
    }

    @Transactional
    public void decorateAndBindAutoTags(Video video) {
        bindAutoTags(video);
        decorateVideo(video);
    }

    @Transactional
    public void bindCustomTags(Video video, List<String> customTagNames) {
        if (video == null || video.getId() == null) {
            return;
        }

        QueryWrapper<VideoTag> cleanupWrapper = new QueryWrapper<>();
        cleanupWrapper.eq("video_id", video.getId());
        videoTagMapper.delete(cleanupWrapper);

        for (String name : normalizeCustomTagNames(customTagNames)) {
            Tag tag = findOrCreateTag(name);
            VideoTag videoTag = new VideoTag();
            videoTag.setVideoId(video.getId());
            videoTag.setTagId(tag.getId());
            videoTagMapper.insert(videoTag);
        }
    }

    @Transactional
    public void decorateAndBindTags(Video video, List<String> customTagNames) {
        if (normalizeCustomTagNames(customTagNames).isEmpty()) {
            decorateAndBindAutoTags(video);
            return;
        }
        bindCustomTags(video, customTagNames);
        decorateVideo(video);
    }

    private void decorateVideo(Video video) {
        video.setVideoId(video.getId());
        if (video.getPlayCount() != null) {
            video.setViews(formatPlayCount(video.getPlayCount()));
        }
        setVideoSources(video);
        attachAuthorInfo(video);
        attachTags(video);
    }

    private void attachAuthorInfo(Video video) {
        if (!StringUtils.hasText(video.getAuthor())) {
            return;
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("nickname", video.getAuthor().trim()).or().eq("username", video.getAuthor().trim());
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            return;
        }
        Video.Author author = new Video.Author();
        author.setUserId(user.getId());
        author.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        author.setAvatarUrl(user.getAvatarUrl());
        video.setAuthorInfo(author);
    }

    private boolean isVideoAuthor(Video video, Long userId) {
        if (video == null || userId == null || !StringUtils.hasText(video.getAuthor())) {
            return false;
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("nickname", video.getAuthor().trim()).or().eq("username", video.getAuthor().trim());
        User user = userMapper.selectOne(queryWrapper);
        return user != null && userId.equals(user.getId());
    }

    private void setVideoSources(Video video) {
        Map<String, String> sources = new HashMap<>();
        if (StringUtils.hasText(video.getUrl240p())) {
            sources.put("240P", video.getUrl240p());
        }
        if (StringUtils.hasText(video.getUrl360p())) {
            sources.put("360P", video.getUrl360p());
        }
        if (StringUtils.hasText(video.getUrl480p())) {
            sources.put("480P", video.getUrl480p());
        }
        if (StringUtils.hasText(video.getUrl720p())) {
            sources.put("720P", video.getUrl720p());
        }
        if (StringUtils.hasText(video.getUrl1080p())) {
            sources.put("1080P", video.getUrl1080p());
        }
        if (sources.isEmpty() && StringUtils.hasText(video.getPlayUrl())) {
            sources.put("720P", video.getPlayUrl());
        }
        video.setSources(sources);
        video.setDefaultQuality(video.getDefaultQuality() != null
                ? video.getDefaultQuality()
                : (sources.containsKey("720P") ? "720P" : sources.keySet().stream().findFirst().orElse("720P")));
    }

    private void attachTags(Video video) {
        List<VideoTag> relations = videoTagMapper.selectList(new QueryWrapper<VideoTag>().eq("video_id", video.getId()));
        if (relations.isEmpty()) {
            video.setTags(new ArrayList<>());
            return;
        }
        List<Long> tagIds = relations.stream().map(VideoTag::getTagId).distinct().toList();
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        Map<Long, Tag> tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, tag -> tag));
        List<Tag> orderedTags = tagIds.stream().map(tagMap::get).filter(tag -> tag != null).toList();
        video.setTags(orderedTags);
    }

    private Set<Long> getTagIdsByVideoId(Long videoId) {
        return videoTagMapper.selectList(new QueryWrapper<VideoTag>().eq("video_id", videoId))
                .stream()
                .map(VideoTag::getTagId)
                .collect(Collectors.toSet());
    }

    private Map<Long, Integer> buildPreferredTagWeights(Long userId) {
        Map<Long, Integer> weights = new HashMap<>();
        if (userId == null) {
            return weights;
        }

        List<ViewHistory> histories = viewHistoryMapper.selectList(new QueryWrapper<ViewHistory>().eq("user_id", userId));
        for (ViewHistory history : histories) {
            int weight = Math.max(history.getViewCount() == null ? 1 : history.getViewCount(), 1);
            addVideoTagsToWeights(weights, history.getVideoId(), Math.min(weight, 5));
        }

        List<UserVideo> userVideos = userVideoMapper.selectList(new QueryWrapper<UserVideo>().eq("user_id", userId));
        for (UserVideo userVideo : userVideos) {
            if (Boolean.TRUE.equals(userVideo.getLiked())) {
                addVideoTagsToWeights(weights, userVideo.getVideoId(), 4);
            }
            if (Boolean.TRUE.equals(userVideo.getFavorited())) {
                addVideoTagsToWeights(weights, userVideo.getVideoId(), 6);
            }
        }

        return weights;
    }

    private void addVideoTagsToWeights(Map<Long, Integer> weights, Long videoId, int delta) {
        if (videoId == null || delta <= 0) {
            return;
        }
        for (Long tagId : getTagIdsByVideoId(videoId)) {
            weights.merge(tagId, delta, Integer::sum);
        }
    }

    private Set<Long> getInteractedVideoIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        if (userId == null) {
            return ids;
        }

        viewHistoryMapper.selectList(new QueryWrapper<ViewHistory>().eq("user_id", userId))
                .forEach(history -> {
                    if (history.getVideoId() != null) {
                        ids.add(history.getVideoId());
                    }
                });
        userVideoMapper.selectList(new QueryWrapper<UserVideo>().eq("user_id", userId))
                .forEach(userVideo -> {
                    if (userVideo.getVideoId() != null
                            && (Boolean.TRUE.equals(userVideo.getLiked()) || Boolean.TRUE.equals(userVideo.getFavorited()))) {
                        ids.add(userVideo.getVideoId());
                    }
                });
        return ids;
    }

    private int calculateRecommendationScore(Video video, Map<Long, Integer> preferredTagWeights, Set<Long> interactedVideoIds) {
        int score = 0;
        if (video == null || video.getId() == null) {
            return score;
        }

        for (Long tagId : getTagIdsByVideoId(video.getId())) {
            score += preferredTagWeights.getOrDefault(tagId, 0) * 100;
        }

        if (interactedVideoIds.contains(video.getId())) {
            score -= 20;
        }

        score += calculateHotScore(video);
        if (video.getCreatedAt() != null && video.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            score += 20;
        }
        return score;
    }

    private int calculateHotScore(Video video) {
        int playCount = video.getPlayCount() == null ? 0 : video.getPlayCount();
        int likeCount = video.getLikeCount() == null ? 0 : video.getLikeCount();
        int favoriteCount = video.getFavoriteCount() == null ? 0 : video.getFavoriteCount();
        int commentCount = video.getCommentCount() == null ? 0 : video.getCommentCount();
        return Math.min(playCount, 50000) / 80
                + Math.min(likeCount, 5000) / 4
                + Math.min(favoriteCount, 3000) / 3
                + Math.min(commentCount, 2000) / 4;
    }

    private boolean matchesKeyword(Video video, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return containsIgnoreCase(video.getTitle(), keyword)
                || containsIgnoreCase(video.getAuthor(), keyword)
                || containsIgnoreCase(video.getDescription(), keyword)
                || (video.getTags() != null && video.getTags().stream().anyMatch(tag -> containsIgnoreCase(tag.getName(), keyword)));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Tag findOrCreateTag(String rawName) {
        String name = rawName.trim();
        QueryWrapper<Tag> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", name);
        Tag existing = tagMapper.selectOne(queryWrapper);
        if (existing != null) {
            return existing;
        }
        Tag tag = new Tag();
        tag.setName(name);
        tagMapper.insert(tag);
        return tag;
    }

    private List<String> generateTagNames(Video video) {
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        addGeneratedTag(ordered, inferTagFromText(video.getTitle()));
        addGeneratedTag(ordered, inferTagFromText(video.getDescription()));
        addGeneratedTag(ordered, inferDurationTag(video.getDuration()));
        addGeneratedTag(ordered, inferTypeTag(video));
        addGeneratedTag(ordered, normalizeKeyword(video.getAuthor()));
        return ordered.values().stream().limit(5).toList();
    }

    private void addGeneratedTag(Map<String, String> ordered, String tagName) {
        if (!StringUtils.hasText(tagName)) {
            return;
        }
        String normalizedKey = tagName.trim().toLowerCase(Locale.ROOT);
        ordered.putIfAbsent(normalizedKey, tagName.trim());
    }

    private String inferTagFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("直播")) return "直播";
        if (normalized.contains("音乐") || normalized.contains("歌") || normalized.contains("演唱")) return "音乐";
        if (normalized.contains("舞") || normalized.contains("跳舞")) return "舞蹈";
        if (normalized.contains("学习") || normalized.contains("课程") || normalized.contains("教学")) return "学习";
        if (normalized.contains("游戏")) return "游戏";
        if (normalized.contains("校园") || normalized.contains("社团")) return "校园";
        if (normalized.contains("比赛") || normalized.contains("决赛")) return "赛事";
        if (normalized.contains("vlog") || normalized.contains("日常")) return "日常";

        List<String> words = extractKeywords(text);
        return words.isEmpty() ? null : words.get(0);
    }

    private String inferDurationTag(String duration) {
        int seconds = parseDurationToSeconds(duration);
        if (seconds <= 0) {
            return null;
        }
        if (seconds < 180) {
            return "短视频";
        }
        if (seconds < 1200) {
            return "中视频";
        }
        return "长视频";
    }

    private String inferTypeTag(Video video) {
        if (StringUtils.hasText(video.getPlayUrl()) && !StringUtils.hasText(video.getUrl240p()) && !StringUtils.hasText(video.getUrl1080p())) {
            return "录播";
        }
        return "视频";
    }

    private String normalizeKeyword(String text) {
        List<String> words = extractKeywords(text);
        return words.isEmpty() ? null : words.get(0);
    }

    private List<String> normalizeCustomTagNames(List<String> customTagNames) {
        if (customTagNames == null || customTagNames.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String rawName : customTagNames) {
            if (!StringUtils.hasText(rawName)) {
                continue;
            }
            String[] segments = rawName.split("[,，]");
            for (String segment : segments) {
                if (!StringUtils.hasText(segment)) {
                    continue;
                }
                String normalized = segment.trim();
                String key = normalized.toLowerCase(Locale.ROOT);
                if (normalized.length() < 1 || normalized.length() > 20) {
                    continue;
                }
                ordered.putIfAbsent(key, normalized);
            }
        }
        return new ArrayList<>(ordered.values());
    }

    private List<String> extractKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String cleaned = text.replaceAll("[^\\p{IsHan}A-Za-z0-9 ]", " ").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        String[] parts = cleaned.split("\\s+");
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : parts) {
            String candidate = part.trim();
            if (candidate.length() < 2 || candidate.length() > 8) {
                continue;
            }
            String key = candidate.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                result.add(candidate);
            }
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    private int parseDurationToSeconds(String duration) {
        if (!StringUtils.hasText(duration)) {
            return 0;
        }
        String[] parts = duration.trim().split(":");
        try {
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
            }
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return 0;
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

    private ViewHistory recordViewHistory(Long userId, Long videoId, boolean increaseViewCount) {
        if (userId == null) {
            return null;
        }

        QueryWrapper<ViewHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("video_id", videoId);
        ViewHistory history = viewHistoryMapper.selectOne(queryWrapper);
        LocalDateTime now = LocalDateTime.now();
        if (history == null) {
            history = new ViewHistory();
            history.setUserId(userId);
            history.setVideoId(videoId);
            history.setViewCount(increaseViewCount ? 1 : 0);
            history.setProgressSeconds(0);
            history.setLastViewedAt(now);
            viewHistoryMapper.insert(history);
            return history;
        }

        int currentViewCount = history.getViewCount() == null ? 0 : history.getViewCount();
        if (increaseViewCount) {
            history.setViewCount(currentViewCount + 1);
        }
        history.setLastViewedAt(now);
        viewHistoryMapper.updateById(history);
        return history;
    }

    private String formatPlayCount(Integer playCount) {
        int safeCount = playCount == null ? 0 : playCount;
        if (safeCount >= 10000) {
            return String.format(Locale.ROOT, "%.1f万", safeCount / 10000.0);
        }
        return String.valueOf(safeCount);
    }
}
