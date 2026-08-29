package com.example.video.repository;

import com.example.video.model.Video;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class JdbcVideoRepository implements VideoRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcVideoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Video> findPublicVideos(Integer categoryId) {
        String sql = "SELECT id, title, description, cover_url, play_url, video_url, author, user_id, "
                + "category_id, tags, duration, status, play_count, like_count, favorite_count, "
                + "comment_count, created_at, updated_at FROM video WHERE status = 'public'"
                + (categoryId != null && categoryId > 0 ? " AND category_id = ?" : "")
                + " ORDER BY created_at DESC, id DESC";
        return categoryId != null && categoryId > 0
                ? jdbcTemplate.query(sql, this::mapVideo, categoryId)
                : jdbcTemplate.query(sql, this::mapVideo);
    }

    @Override
    public Set<Long> findViewedVideoIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new HashSet<>(jdbcTemplate.query(
                "SELECT video_id FROM view_history WHERE user_id = ?",
                (rs, rowNum) -> rs.getLong("video_id"), userId));
    }

    private Video mapVideo(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Video video = new Video();
        video.setId(rs.getLong("id"));
        video.setTitle(rs.getString("title"));
        video.setDescription(rs.getString("description"));
        video.setCoverUrl(rs.getString("cover_url"));
        video.setPlayUrl(rs.getString("play_url"));
        video.setVideoUrl(rs.getString("video_url"));
        video.setAuthor(rs.getString("author"));
        long userId = rs.getLong("user_id");
        video.setUserId(rs.wasNull() ? null : userId);
        int categoryId = rs.getInt("category_id");
        video.setCategoryId(rs.wasNull() ? null : categoryId);
        video.setTags(rs.getString("tags"));
        int duration = rs.getInt("duration");
        video.setDuration(rs.wasNull() ? null : duration);
        video.setStatus(rs.getString("status"));
        video.setPlayCount(nullableInt(rs, "play_count"));
        video.setLikeCount(nullableInt(rs, "like_count"));
        video.setFavoriteCount(nullableInt(rs, "favorite_count"));
        video.setCommentCount(nullableInt(rs, "comment_count"));
        Timestamp created = rs.getTimestamp("created_at");
        video.setCreatedAt(created == null ? null : created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        video.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        return video;
    }

    private Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
