package com.example.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.video.client.UserPreferenceClient;
import com.example.video.entity.UserVideo;
import com.example.video.entity.Video;
import com.example.video.entity.ViewHistory;
import com.example.video.mapper.UserVideoMapper;
import com.example.video.mapper.VideoMapper;
import com.example.video.mapper.ViewHistoryMapper;
import com.example.video.model.UserPreference;
import com.example.video.service.MinioService;
import com.example.video.service.VideoService;
import com.example.video.service.VideoTranscodeService;
import com.example.video.vo.VideoVO;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {
    private final UserVideoMapper userVideoMapper;
    private final ViewHistoryMapper viewHistoryMapper;
    private final UserPreferenceClient userPreferenceClient;
    private final MinioService minioService;
    private final VideoTranscodeService transcodeService;
    private final RestClient userProfileClient;

    @Autowired
    public VideoServiceImpl(UserVideoMapper userVideoMapper, ViewHistoryMapper viewHistoryMapper,
                            UserPreferenceClient userPreferenceClient,
                            MinioService minioService, VideoTranscodeService transcodeService,
                            org.springframework.core.env.Environment environment) {
        this.userVideoMapper = userVideoMapper;
        this.viewHistoryMapper = viewHistoryMapper;
        this.userPreferenceClient = userPreferenceClient;
        this.minioService = minioService;
        this.transcodeService = transcodeService;
        this.userProfileClient = RestClient.builder()
                .baseUrl(environment.getProperty("user-service.base-url", "http://user-service:8081"))
                .build();
    }

    /** 保留无 Environment 的测试构造器。 */
    public VideoServiceImpl(UserVideoMapper userVideoMapper, ViewHistoryMapper viewHistoryMapper,
                            UserPreferenceClient userPreferenceClient,
                            MinioService minioService, VideoTranscodeService transcodeService) {
        this(userVideoMapper, viewHistoryMapper, userPreferenceClient, minioService, transcodeService,
                new org.springframework.core.env.StandardEnvironment());
    }

    @Override
    public VideoVO upload(MultipartFile file, String title, String description, String coverUrl, String tags, String author, Long userId, Integer duration) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("视频标题不能为空");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的视频文件");
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("upload-video-", extension(file.getOriginalFilename()));
            file.transferTo(tempFile);
            String objectName = "videos/video-" + UUID.randomUUID().toString().replace("-", "") + extension(file.getOriginalFilename());
            minioService.upload(tempFile, objectName, file.getContentType());
            String playUrl = minioService.publicUrl(objectName);

            Map<String, String> quality = transcodeService.transcodeAndUpload(tempFile, "videos/qualities");
            Video video = new Video();
            video.setTitle(title.trim());
            video.setDescription(description);
            video.setCoverUrl(persistCoverUrl(coverUrl));
            video.setPlayUrl(playUrl);
            video.setAuthor(author == null || author.isBlank() ? "匿名用户" : author.trim());
            video.setUserId(userId);
            video.setDuration(duration);
            video.setStatus("public");
            video.setPlayCount(0);
            video.setLikeCount(0);
            video.setFavoriteCount(0);
            video.setCommentCount(0);
            video.setDefaultQuality(quality.isEmpty() ? "原画" : quality.keySet().stream().max(Comparator.comparingInt(this::qualityRank)).orElse("原画"));
            video.setVideoUrl(objectName);
            video.setTags(normalizeTags(tags));
            if (quality.containsKey("240P")) video.setUrl240p(minioService.publicUrl(quality.get("240P")));
            if (quality.containsKey("360P")) video.setUrl360p(minioService.publicUrl(quality.get("360P")));
            if (quality.containsKey("480P")) video.setUrl480p(minioService.publicUrl(quality.get("480P")));
            if (quality.containsKey("720P")) video.setUrl720p(minioService.publicUrl(quality.get("720P")));
            if (quality.containsKey("1080P")) video.setUrl1080p(minioService.publicUrl(quality.get("1080P")));
            video.setCreatedAt(LocalDateTime.now());
            save(video);
            return toVO(getById(video.getId()), null);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("视频上传失败: " + exception.getMessage(), exception);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    @Override
    public List<VideoVO> recommend(int page, int pageSize, Long categoryId, String keyword, Long viewerId) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        long fromIndex = ((long) safePage - 1) * safeSize;
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        QueryWrapper<Video> qw = new QueryWrapper<>();
        qw.eq("status", "public");
        if (categoryId != null && categoryId > 0) qw.eq("category_id", categoryId);
        if (!kw.isBlank()) qw.and(w -> w.like("title", kw).or().like("author", kw).or().like("description", kw).or().like("tags", kw));
        List<Video> videos = new ArrayList<>(list(qw));

        // 个性化偏好：从 user-service 获取关注作者/兴趣/已看，失败时降级为 guest
        UserPreference preference = viewerId == null ? UserPreference.guest() : safePreference(viewerId);
        Set<Long> followed = preference.followedAuthorIds();
        Set<Long> viewed = new HashSet<>(preference.viewedVideoIds());
        viewed.addAll(localViewedVideoIds(viewerId));
        Map<String, Integer> interests = normalizeInterests(preference.interests());

        videos.sort(Comparator.comparingLong((Video video) -> score(video, followed, viewed, interests)).reversed()
                .thenComparing(Video::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Video::getId, Comparator.nullsLast(Comparator.reverseOrder())));

        if (fromIndex >= videos.size()) return List.of();
        int start = (int) fromIndex;
        List<VideoVO> result = videos.subList(start, Math.min(start + safeSize, videos.size()))
                .stream().map(v -> toVO(v, viewerId)).toList();
        // 契约字段：authorInfo.following —— 当前用户是否关注该作者
        if (!followed.isEmpty()) {
            result.forEach(vo -> {
                if (vo.getAuthorInfo() != null && vo.getUserId() != null && followed.contains(vo.getUserId())) {
                    vo.getAuthorInfo().setFollowing(true);
                }
            });
        }
        return result;
    }

    private UserPreference safePreference(Long userId) {
        UserPreference preference = userPreferenceClient.getPreference(userId);
        return preference == null ? UserPreference.guest() : preference;
    }

    private Set<Long> localViewedVideoIds(Long userId) {
        if (userId == null) return Set.of();
        return viewHistoryMapper.selectList(new QueryWrapper<ViewHistory>().eq("user_id", userId))
                .stream().map(ViewHistory::getVideoId).collect(Collectors.toSet());
    }

    private Map<String, Integer> normalizeInterests(Map<String, Integer> interests) {
        if (interests == null || interests.isEmpty()) return Map.of();
        return interests.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT),
                        entry -> entry.getValue() == null ? 0 : Math.max(entry.getValue(), 0),
                        Integer::sum));
    }

    private long score(Video video, Set<Long> followed, Set<Long> viewed, Map<String, Integer> interests) {
        long result = 0L;
        result += nonNegative(video.getPlayCount());
        result += nonNegative(video.getLikeCount()) * 20L;
        result += nonNegative(video.getFavoriteCount()) * 30L;
        result += nonNegative(video.getCommentCount()) * 10L;
        if (video.getUserId() != null && followed.contains(video.getUserId())) result += 1000L;
        for (String tag : parseTags(video.getTags())) {
            result += interests.getOrDefault(tag.toLowerCase(Locale.ROOT), 0) * 10L;
        }
        if (video.getId() != null && viewed.contains(video.getId())) result -= 200L;
        return result;
    }

    private long nonNegative(Integer value) {
        return value == null ? 0L : Math.max(value, 0);
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return List.of(tags.split("[,，\\s]+")).stream()
                .map(String::trim).filter(tag -> !tag.isBlank()).distinct().limit(8).toList();
    }

    @Override
    public VideoVO getByIdWithState(Long videoId, Long viewerId) {
        Video video = getById(videoId);
        if (video == null || "deleted".equals(video.getStatus())) throw new IllegalArgumentException("视频已不可见");
        return toVO(video, viewerId);
    }

    @Override
    public List<VideoVO> getByUserId(Long userId, Long viewerId) {
        return list(new QueryWrapper<Video>().eq("user_id", userId).ne("status", "deleted")
                .orderByDesc("created_at").orderByDesc("id")).stream().map(v -> toVO(v, viewerId)).toList();
    }

    @Override
    public List<VideoVO> getFavoritesByUserId(Long userId, Long viewerId) {
        requirePositive(userId, "用户 ID");
        List<UserVideo> relations = userVideoMapper.selectList(new QueryWrapper<UserVideo>()
                .eq("user_id", userId).eq("favorited", true).orderByDesc("updated_at", "created_at"));
        return relations.stream().map(r -> getById(r.getVideoId())).filter(v -> v != null && "public".equals(v.getStatus()))
                .map(v -> toVO(v, viewerId)).toList();
    }

    @Override
    public boolean softDelete(Long videoId, Long userId) {
        requirePositive(videoId, "视频 ID");
        requirePositive(userId, "用户 ID");
        Video video = getById(videoId);
        if (video == null || !userId.equals(video.getUserId()) || "deleted".equals(video.getStatus())) return false;
        video.setStatus("deleted");
        return updateById(video);
    }

    @Override
    public boolean setVisibility(Long videoId, Long userId, boolean visible) {
        requirePositive(videoId, "视频 ID");
        requirePositive(userId, "用户 ID");
        Video video = getById(videoId);
        if (video == null || !userId.equals(video.getUserId()) || "deleted".equals(video.getStatus())) return false;
        video.setStatus(visible ? "public" : "private");
        return updateById(video);
    }

    @Override
    public Map<String, Object> play(Long videoId, Long userId) {
        Video video = getById(videoId);
        if (video == null || "deleted".equals(video.getStatus())) throw new IllegalArgumentException("视频已不可见");
        video.setPlayCount((video.getPlayCount() == null ? 0 : video.getPlayCount()) + 1);
        updateById(video);
        upsertViewHistory(userId, videoId);
        return Map.of("videoId", videoId, "playCount", video.getPlayCount());
    }

    @Override
    public Map<String, Object> toggleLike(Long userId, Long videoId) {
        requirePositive(userId, "用户 ID");
        Video video = getById(videoId);
        if (video == null) throw new IllegalArgumentException("视频不存在");
        boolean newState = flipRelation(userId, videoId, true);
        video.setLikeCount(Math.max(0, (video.getLikeCount() == null ? 0 : video.getLikeCount()) + (newState ? 1 : -1)));
        updateById(video);
        return Map.of("videoId", videoId, "liked", newState, "likeCount", video.getLikeCount());
    }

    @Override
    public Map<String, Object> toggleFavorite(Long userId, Long videoId) {
        requirePositive(userId, "用户 ID");
        Video video = getById(videoId);
        if (video == null) throw new IllegalArgumentException("视频不存在");
        boolean newState = flipRelation(userId, videoId, false);
        video.setFavoriteCount(Math.max(0, (video.getFavoriteCount() == null ? 0 : video.getFavoriteCount()) + (newState ? 1 : -1)));
        updateById(video);
        return Map.of("videoId", videoId, "favorited", newState, "favoriteCount", video.getFavoriteCount());
    }

    @Override
    public Map<String, Object> status(Long userId, Long videoId) {
        Video video = getById(videoId);
        if (video == null) throw new IllegalArgumentException("视频不存在");
        UserVideo relation = relation(userId, videoId);
        return Map.of("videoId", videoId,
                "liked", relation != null && Boolean.TRUE.equals(relation.getLiked()),
                "favorited", relation != null && Boolean.TRUE.equals(relation.getFavorited()),
                "status", video.getStatus());
    }

    @Override
    public List<Map<String, Object>> history(Long userId) {
        if (userId == null) return List.of();
        return viewHistoryMapper.selectList(new QueryWrapper<ViewHistory>().eq("user_id", userId)
                        .orderByDesc("last_viewed_at").last("limit 100"))
                .stream().map(h -> {
                    Map<String,Object> item = new LinkedHashMap<>();
                    Video video = getById(h.getVideoId());
                    // 保留历史记录，即使稿件已软删除；前端据此提示视频不可见。
                    item.put("videoId", h.getVideoId());
                    item.put("video", video == null || "deleted".equals(video.getStatus())
                            ? null : toVO(video, userId));
                    item.put("videoStatus", video == null ? "missing" : video.getStatus());
                    item.put("viewCount", h.getViewCount());
                    item.put("lastViewedAt", h.getLastViewedAt());
                    return item;
                }).toList();
    }

    private boolean flipRelation(Long userId, Long videoId, boolean like) {
        UserVideo relation = relation(userId, videoId);
        boolean newState;
        if (relation == null) {
            relation = new UserVideo();
            relation.setUserId(userId);
            relation.setVideoId(videoId);
            relation.setLiked(like);
            relation.setFavorited(!like);
            relation.setCreatedAt(LocalDateTime.now());
            userVideoMapper.insert(relation);
            newState = true;
        } else {
            if (like) { newState = !Boolean.TRUE.equals(relation.getLiked()); relation.setLiked(newState); }
            else { newState = !Boolean.TRUE.equals(relation.getFavorited()); relation.setFavorited(newState); }
            userVideoMapper.updateById(relation);
        }
        return newState;
    }

    private UserVideo relation(Long userId, Long videoId) {
        if (userId == null) return null;
        return userVideoMapper.selectOne(new QueryWrapper<UserVideo>().eq("user_id", userId).eq("video_id", videoId));
    }

    private void upsertViewHistory(Long userId, Long videoId) {
        if (userId == null) return;
        ViewHistory history = viewHistoryMapper.selectOne(new QueryWrapper<ViewHistory>().eq("user_id", userId).eq("video_id", videoId));
        if (history == null) {
            history = new ViewHistory();
            history.setUserId(userId);
            history.setVideoId(videoId);
            history.setViewCount(1);
            history.setProgressSeconds(0);
            history.setLastViewedAt(LocalDateTime.now());
            viewHistoryMapper.insert(history);
        } else {
            history.setViewCount((history.getViewCount() == null ? 0 : history.getViewCount()) + 1);
            history.setLastViewedAt(LocalDateTime.now());
            viewHistoryMapper.updateById(history);
        }
    }

    private VideoVO toVO(Video video, Long viewerId) {
        VideoVO vo = new VideoVO();
        vo.setId(video.getId());
        vo.setVideoId(video.getId());
        vo.setTitle(video.getTitle());
        vo.setDescription(video.getDescription());
        vo.setCoverUrl(video.getCoverUrl());
        vo.setPlayUrl(video.getPlayUrl());
        vo.setAuthor(video.getAuthor());
        vo.setUserId(video.getUserId());
        vo.setCategoryId(video.getCategoryId());
        vo.setTags(video.getTags());
        vo.setDuration(video.getDuration());
        vo.setStatus(video.getStatus());
        vo.setPlayCount(video.getPlayCount());
        vo.setLikeCount(video.getLikeCount());
        vo.setFavoriteCount(video.getFavoriteCount());
        vo.setCommentCount(video.getCommentCount());
        vo.setDefaultQuality(video.getDefaultQuality());
        vo.setViews(formatPlayCount(video.getPlayCount()));
        vo.setSources(sources(video));
        if (viewerId != null) {
            UserVideo relation = relation(viewerId, video.getId());
            vo.setLiked(relation != null && Boolean.TRUE.equals(relation.getLiked()));
            vo.setFavorited(relation != null && Boolean.TRUE.equals(relation.getFavorited()));
        }
        vo.setCreatedAt(video.getCreatedAt());
        vo.setUpdatedAt(video.getUpdatedAt());
        vo.setVideoUrl(video.getVideoUrl());
        vo.setTagList(parseTags(video.getTags()));
        if (video.getUserId() != null) {
            VideoVO.AuthorInfo authorInfo = new VideoVO.AuthorInfo(video.getUserId(), false);
            authorInfo.setNickname(video.getAuthor());
            try {
                JsonNode root = userProfileClient.get().uri("/api/user/{id}/profile", video.getUserId())
                        .retrieve().body(JsonNode.class);
                JsonNode data = root != null && root.has("data") ? root.path("data") : root;
                if (data != null && !data.isMissingNode()) {
                    if (data.hasNonNull("nickname")) authorInfo.setNickname(data.get("nickname").asText());
                    if (data.hasNonNull("avatarUrl")) authorInfo.setAvatarUrl(data.get("avatarUrl").asText());
                }
            } catch (RuntimeException ignored) { }
            vo.setAuthorInfo(authorInfo);
        }
        return vo;
    }

    private Map<String, String> sources(Video video) {
        Map<String, String> sources = new LinkedHashMap<>();
        putIfPresent(sources, "480P", video.getUrl480p());
        putIfPresent(sources, "720P", video.getUrl720p());
        putIfPresent(sources, "1080P", video.getUrl1080p());
        if (sources.isEmpty() && video.getPlayUrl() != null) sources.put("原画", video.getPlayUrl());
        return sources;
    }

    private void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }

    private String formatPlayCount(Integer playCount) {
        int count = playCount == null ? 0 : playCount;
        if (count >= 10_000) return String.format("%.1f万", count / 10_000.0);
        return String.valueOf(count);
    }

    private String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) return "";
        return List.of(tags.split("[,，\\s]+")).stream()
                .map(String::trim).filter(StringUtils::hasText).distinct().limit(8)
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private String persistCoverUrl(String coverUrl) {
        if (coverUrl == null || coverUrl.isBlank()) return null;
        String value = coverUrl.trim();
        if (!value.startsWith("data:")) return value.length() <= 500 ? value : null;
        try {
            int comma = value.indexOf(',');
            if (comma < 0) return null;
            String meta = value.substring(5, comma);
            String contentType = meta.startsWith("image/") ? meta.split(";")[0] : "image/jpeg";
            byte[] bytes = Base64.getDecoder().decode(value.substring(comma + 1));
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) return null;
            String ext = contentType.contains("png") ? ".png" : ".jpg";
            String object = "covers/cover-" + UUID.randomUUID().toString().replace("-", "") + ext;
            minioService.upload(bytes, object, contentType);
            return minioService.publicUrl(object);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int qualityRank(String quality) {
        try { return Integer.parseInt(quality.replace("P", "")); } catch (Exception e) { return 0; }
    }

    private String extension(String filename) {
        if (filename == null) return ".mp4";
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(index) : ".mp4";
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) throw new IllegalArgumentException(name + "不合法");
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 忽略清理失败
        }
    }
}
