package com.example.video.service;

import com.example.video.client.UserPreferenceClient;
import com.example.video.model.UserPreference;
import com.example.video.model.Video;
import com.example.video.repository.VideoRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {
    private final VideoRepository videoRepository;
    private final UserPreferenceClient userPreferenceClient;

    public RecommendationServiceImpl(VideoRepository videoRepository,
                                      UserPreferenceClient userPreferenceClient) {
        this.videoRepository = videoRepository;
        this.userPreferenceClient = userPreferenceClient;
    }

    @Override
    public List<Video> recommend(Long userId, Integer page, Integer pageSize,
                                 Integer categoryId, String keyword) {
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safePageSize = Math.min(Math.max(pageSize == null ? 12 : pageSize, 1), 50);
        long fromIndex = ((long) safePage - 1L) * safePageSize;

        List<Video> videos = new ArrayList<>(videoRepository.findPublicVideos(categoryId));
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (!normalizedKeyword.isBlank()) {
            videos = videos.stream()
                    .filter(video -> matches(video, normalizedKeyword))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        UserPreference preference = userId == null
                ? UserPreference.guest()
                : safePreference(userId);
        Set<Long> followedAuthorIds = preference.followedAuthorIds();
        Set<Long> viewedVideoIds = new HashSet<>(videoRepository.findViewedVideoIds(userId));
        if (preference.viewedVideoIds() != null) {
            viewedVideoIds.addAll(preference.viewedVideoIds());
        }
        Map<String, Integer> interests = normalizeInterests(preference.interests());

        videos.forEach(video -> decorate(video, followedAuthorIds));
        videos.sort(Comparator
                .comparingLong((Video video) -> score(video, followedAuthorIds, viewedVideoIds, interests))
                .reversed()
                .thenComparing(Video::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Video::getId, Comparator.nullsLast(Comparator.reverseOrder())));

        if (fromIndex >= videos.size()) {
            return List.of();
        }
        int start = (int) fromIndex;
        return List.copyOf(videos.subList(start, Math.min(start + safePageSize, videos.size())));
    }

    private UserPreference safePreference(Long userId) {
        UserPreference preference = userPreferenceClient.getPreference(userId);
        return preference == null ? UserPreference.guest() : preference;
    }

    private boolean matches(Video video, String keyword) {
        return contains(video.getTitle(), keyword)
                || contains(video.getAuthor(), keyword)
                || contains(video.getDescription(), keyword)
                || contains(video.getTags(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void decorate(Video video, Set<Long> followedAuthorIds) {
        video.setVideoId(video.getId());
        video.setSources(buildSources(video));
        video.setDefaultQuality(video.getDefaultQuality() == null ? "720P" : video.getDefaultQuality());
        video.setTagList(parseTags(video.getTags()));
        if (video.getUserId() != null) {
            Video.Author author = video.getAuthorInfo();
            if (author == null) {
                author = new Video.Author();
                author.setUserId(video.getUserId());
            }
            author.setFollowing(followedAuthorIds.contains(video.getUserId()));
            video.setAuthorInfo(author);
        }
    }

    private Map<String, String> buildSources(Video video) {
        Map<String, String> sources = new LinkedHashMap<>();
        if (hasText(video.getPlayUrl())) {
            sources.put("720P", video.getPlayUrl());
        }
        return sources;
    }

    private List<String> parseTags(String tags) {
        if (!hasText(tags)) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split("[,，\\s]+"))
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .limit(8)
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private long score(Video video, Set<Long> followedAuthorIds,
                       Set<Long> viewedVideoIds, Map<String, Integer> interests) {
        long result = 0L;
        result += nonNegative(video.getPlayCount()) * 1L;
        result += nonNegative(video.getLikeCount()) * 20L;
        result += nonNegative(video.getFavoriteCount()) * 30L;
        result += nonNegative(video.getCommentCount()) * 10L;
        if (video.getUserId() != null && followedAuthorIds.contains(video.getUserId())) {
            result += 1000L;
        }
        for (String tag : parseTags(video.getTags())) {
            result += interests.getOrDefault(tag.toLowerCase(Locale.ROOT), 0) * 10L;
        }
        if (video.getId() != null && viewedVideoIds.contains(video.getId())) {
            result -= 200L;
        }
        return result;
    }

    private long nonNegative(Integer value) {
        return value == null ? 0L : Math.max(value, 0);
    }

    private Map<String, Integer> normalizeInterests(Map<String, Integer> interests) {
        if (interests == null || interests.isEmpty()) {
            return Map.of();
        }
        return interests.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT),
                        entry -> entry.getValue() == null ? 0 : Math.max(entry.getValue(), 0),
                        Integer::sum));
    }
}
