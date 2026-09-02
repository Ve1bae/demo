package com.example.video.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.video.common.PageResult;
import com.example.video.entity.Comment;
import com.example.video.entity.CommentLike;
import com.example.video.entity.Video;
import com.example.video.mapper.CommentLikeMapper;
import com.example.video.mapper.CommentMapper;
import com.example.video.mapper.VideoMapper;
import com.example.video.vo.CommentVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CommentService {
    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final VideoMapper videoMapper;

    public CommentService(CommentMapper commentMapper, CommentLikeMapper commentLikeMapper, VideoMapper videoMapper) {
        this.commentMapper = commentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.videoMapper = videoMapper;
    }

    public PageResult<CommentVO> list(Long videoId, int page, int pageSize, Long viewerId) {
        if (videoId == null || videoId <= 0) throw new IllegalArgumentException("视频 ID 不合法");
        if (videoMapper.selectById(videoId) == null) throw new IllegalArgumentException("视频不存在");
        long safePage = page <= 0 ? 1 : page;
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        List<Comment> comments = commentMapper.selectList(new QueryWrapper<Comment>()
                .eq("video_id", videoId).orderByDesc("created_at").orderByDesc("id")
                .last("LIMIT " + safeSize + " OFFSET " + ((safePage - 1) * safeSize)));
        long total = commentMapper.selectCount(new QueryWrapper<Comment>().eq("video_id", videoId));
        return new PageResult<>(comments.stream().map(c -> toVO(c, viewerId)).toList(), total, safePage, safeSize);
    }

    public CommentVO add(Long videoId, Long userId, String username, String content, Long parentId) {
        if (videoId == null || videoId <= 0) throw new IllegalArgumentException("视频 ID 不合法");
        if (userId == null || userId <= 0) throw new IllegalArgumentException("用户 ID 不合法");
        if (content == null || content.isBlank() || content.length() > 500) throw new IllegalArgumentException("评论内容为空或超过500字");
        if (videoMapper.selectById(videoId) == null) throw new IllegalArgumentException("视频不存在");
        Comment comment = new Comment();
        comment.setVideoId(videoId);
        comment.setUserId(userId);
        comment.setUsername(username == null || username.isBlank() ? "用户 " + userId : username.trim());
        comment.setContent(content.trim());
        comment.setParentId(parentId);
        comment.setLikeCount(0);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        Video video = videoMapper.selectById(videoId);
        video.setCommentCount((video.getCommentCount() == null ? 0 : video.getCommentCount()) + 1);
        videoMapper.updateById(video);
        return toVO(comment, userId);
    }

    public Map<String, Object> like(Long commentId, Long userId) {
        if (commentId == null || commentId <= 0) throw new IllegalArgumentException("评论 ID 不合法");
        if (userId == null || userId <= 0) throw new IllegalArgumentException("用户 ID 不合法");
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) throw new IllegalArgumentException("评论不存在");
        boolean exists = commentLikeMapper.selectCount(new QueryWrapper<CommentLike>().eq("user_id", userId).eq("comment_id", commentId)) > 0;
        boolean newState = !exists;
        if (newState) {
            CommentLike like = new CommentLike();
            like.setUserId(userId);
            like.setCommentId(commentId);
            like.setCreatedAt(LocalDateTime.now());
            commentLikeMapper.insert(like);
        } else {
            commentLikeMapper.delete(new QueryWrapper<CommentLike>().eq("user_id", userId).eq("comment_id", commentId));
        }
        comment.setLikeCount(Math.max(0, (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) + (newState ? 1 : -1)));
        commentMapper.updateById(comment);
        return Map.of("commentId", commentId, "liked", newState, "likeCount", comment.getLikeCount());
    }

    public Map<String, Object> unlike(Long commentId, Long userId) {
        if (commentId == null || commentId <= 0) throw new IllegalArgumentException("评论 ID 不合法");
        if (userId == null || userId <= 0) throw new IllegalArgumentException("用户 ID 不合法");
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) throw new IllegalArgumentException("评论不存在");
        int removed = commentLikeMapper.delete(new QueryWrapper<CommentLike>().eq("user_id", userId).eq("comment_id", commentId));
        if (removed > 0) {
            comment.setLikeCount(Math.max(0, (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) - 1));
            commentMapper.updateById(comment);
        }
        return Map.of("commentId", commentId, "liked", false, "likeCount", comment.getLikeCount() == null ? 0 : comment.getLikeCount());
    }

    private CommentVO toVO(Comment comment, Long viewerId) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setVideoId(comment.getVideoId());
        vo.setUserId(comment.getUserId());
        vo.setUsername(comment.getUsername());
        vo.setContent(comment.getContent());
        vo.setParentId(comment.getParentId());
        vo.setLikeCount(comment.getLikeCount());
        if (viewerId != null) {
            vo.setLiked(commentLikeMapper.selectCount(new QueryWrapper<CommentLike>()
                    .eq("user_id", viewerId).eq("comment_id", comment.getId())) > 0);
        }
        vo.setCreatedAt(comment.getCreatedAt());
        return vo;
    }
}
