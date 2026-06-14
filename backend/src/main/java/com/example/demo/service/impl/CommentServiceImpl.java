package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentLike;
import com.example.demo.entity.User;
import com.example.demo.mapper.CommentMapper;
import com.example.demo.mapper.CommentLikeMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.CommentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final UserMapper userMapper;
    private final CommentLikeMapper commentLikeMapper;

    public CommentServiceImpl(UserMapper userMapper, CommentLikeMapper commentLikeMapper) {
        this.userMapper = userMapper;
        this.commentLikeMapper = commentLikeMapper;
    }

    @Override
    public List<Comment> getCommentsByVideoId(Long videoId) {
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_id", videoId);
        queryWrapper.orderByDesc("created_at");
        List<Comment> comments = baseMapper.selectList(queryWrapper);
        populateCommentUsers(comments);
        return comments;
    }

    @Override
    public Comment getCommentById(Long commentId) {
        return getById(commentId);
    }

    @Override
    public Map<String, Object> getCommentsByVideoIdWithPagination(Long videoId, Integer page, Integer pageSize) {
        return getCommentsByVideoIdWithPagination(videoId, page, pageSize, null);
    }

    @Override
    public Map<String, Object> getCommentsByVideoIdWithPagination(Long videoId, Integer page, Integer pageSize, Long viewerId) {
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_id", videoId);
        queryWrapper.orderByDesc("created_at");

        List<Comment> allComments = baseMapper.selectList(queryWrapper);
        int total = allComments.size();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePage - 1) * safePageSize, total);
        int toIndex = Math.min(fromIndex + safePageSize, total);
        List<Comment> comments = allComments.subList(fromIndex, toIndex);

        populateCommentUsers(comments, viewerId);

        Map<String, Object> result = new HashMap<>();
        result.put("list", comments);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        return result;
    }

    @Override
    public boolean saveComment(Comment comment) {
        if (comment.getLikeCount() == null) {
            comment.setLikeCount(0);
        }
        return save(comment);
    }

    @Override
    public boolean deleteComment(Long id) {
        return removeById(id);
    }

    @Override
    @Transactional
    public boolean likeComment(Long commentId, Long userId) {
        if (commentId == null || userId == null) {
            return false;
        }
        Comment comment = getById(commentId);
        if (comment == null) {
            return false;
        }

        CommentLike commentLike = new CommentLike();
        commentLike.setCommentId(commentId);
        commentLike.setUserId(userId);
        try {
            commentLikeMapper.insert(commentLike);
        } catch (DuplicateKeyException e) {
            return false;
        }

        int currentLikeCount = comment.getLikeCount() == null ? 0 : comment.getLikeCount();
        comment.setLikeCount(currentLikeCount + 1);
        return updateById(comment);
    }

    @Override
    @Transactional
    public boolean unlikeComment(Long commentId, Long userId) {
        if (commentId == null || userId == null) {
            return false;
        }
        Comment comment = getById(commentId);
        if (comment == null) {
            return false;
        }

        QueryWrapper<CommentLike> wrapper = new QueryWrapper<>();
        wrapper.eq("comment_id", commentId);
        wrapper.eq("user_id", userId);
        int removed = commentLikeMapper.delete(wrapper);
        if (removed <= 0) {
            return false;
        }

        int currentLikeCount = comment.getLikeCount() == null ? 0 : comment.getLikeCount();
        comment.setLikeCount(Math.max(0, currentLikeCount - 1));
        return updateById(comment);
    }

    private void populateCommentUsers(List<Comment> comments) {
        populateCommentUsers(comments, null);
    }

    private void populateCommentUsers(List<Comment> comments, Long viewerId) {
        comments.forEach(comment -> {
            comment.setCommentId(comment.getId());
            User user = userMapper.selectById(comment.getUserId());
            if (user != null) {
                comment.setUser(user);
            }
            comment.setLiked(isLikedByViewer(comment.getId(), viewerId));
            comment.setReplies(List.of());
        });
    }

    private boolean isLikedByViewer(Long commentId, Long viewerId) {
        if (commentId == null || viewerId == null) {
            return false;
        }
        QueryWrapper<CommentLike> wrapper = new QueryWrapper<>();
        wrapper.eq("comment_id", commentId);
        wrapper.eq("user_id", viewerId);
        return commentLikeMapper.selectCount(wrapper) > 0;
    }
}
