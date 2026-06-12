package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentLike;
import com.example.demo.entity.User;
import com.example.demo.mapper.CommentLikeMapper;
import com.example.demo.mapper.CommentMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.CommentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final UserMapper userMapper;
    private final CommentLikeMapper commentLikeMapper;

    public CommentServiceImpl(UserMapper userMapper, CommentLikeMapper commentLikeMapper) {
        this.userMapper = userMapper;
        this.commentLikeMapper = commentLikeMapper;
    }

    @Override
    public List<Comment> getCommentsByVideoId(Long videoId, Long currentUserId) {
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_id", videoId);
        queryWrapper.orderByDesc("created_at");
        List<Comment> comments = buildNestedComments(baseMapper.selectList(queryWrapper), currentUserId);
        populateCommentUsers(comments, currentUserId);
        return comments;
    }

    @Override
    public Map<String, Object> getCommentsByVideoIdWithPagination(Long videoId, Integer page, Integer pageSize, Long currentUserId) {
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_id", videoId);

        List<Comment> allComments = baseMapper.selectList(queryWrapper);
        List<Comment> rootComments = buildNestedComments(allComments, currentUserId);
        rootComments.sort(Comparator.comparing(Comment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        int total = rootComments.size();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePage - 1) * safePageSize, total);
        int toIndex = Math.min(fromIndex + safePageSize, total);
        List<Comment> comments = rootComments.subList(fromIndex, toIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("list", comments);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        return result;
    }

    @Override
    public Comment getCommentById(Long commentId) {
        return getById(commentId);
    }

    @Override
    public boolean saveComment(Comment comment) {
        if (comment.getLikeCount() == null) {
            comment.setLikeCount(0);
        }
        if (comment.getParentId() != null) {
            Comment parent = getById(comment.getParentId());
            if (parent == null || !parent.getVideoId().equals(comment.getVideoId())) {
                return false;
            }
        }
        return save(comment);
    }

    @Override
    public boolean deleteComment(Long id) {
        QueryWrapper<Comment> replyQuery = new QueryWrapper<>();
        replyQuery.eq("parent_id", id);
        List<Comment> replies = baseMapper.selectList(replyQuery);
        for (Comment reply : replies) {
            deleteComment(reply.getId());
        }

        QueryWrapper<CommentLike> likeQuery = new QueryWrapper<>();
        likeQuery.eq("comment_id", id);
        commentLikeMapper.delete(likeQuery);
        return removeById(id);
    }

    @Override
    public Map<String, Object> likeComment(Long commentId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        Comment comment = getById(commentId);
        if (comment == null) {
            result.put("liked", false);
            result.put("likeCount", 0);
            return result;
        }

        QueryWrapper<CommentLike> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("comment_id", commentId).eq("user_id", userId);
        CommentLike existingLike = commentLikeMapper.selectOne(queryWrapper);
        int currentLikeCount = comment.getLikeCount() == null ? 0 : comment.getLikeCount();

        if (existingLike != null) {
            commentLikeMapper.deleteById(existingLike.getId());
            int newLikeCount = Math.max(currentLikeCount - 1, 0);
            comment.setLikeCount(newLikeCount);
            updateById(comment);
            result.put("liked", false);
            result.put("likeCount", newLikeCount);
            return result;
        }

        CommentLike commentLike = new CommentLike();
        commentLike.setCommentId(commentId);
        commentLike.setUserId(userId);
        commentLikeMapper.insert(commentLike);

        comment.setLikeCount(currentLikeCount + 1);
        updateById(comment);

        result.put("liked", true);
        result.put("likeCount", comment.getLikeCount());
        return result;
    }

    private List<Comment> buildNestedComments(List<Comment> comments, Long currentUserId) {
        populateCommentUsers(comments, currentUserId);

        Map<Long, Comment> commentMap = comments.stream()
                .filter(comment -> comment.getId() != null)
                .collect(Collectors.toMap(Comment::getId, Function.identity(), (left, right) -> left));

        Map<Long, List<Comment>> repliesByRoot = new HashMap<>();
        List<Comment> roots = new ArrayList<>();

        for (Comment comment : comments) {
            comment.setReplies(new ArrayList<>());
            comment.setReplyCount(0);
            if (comment.getParentId() == null || !commentMap.containsKey(comment.getParentId())) {
                comment.setRootId(comment.getId());
                roots.add(comment);
                continue;
            }

            Comment parent = commentMap.get(comment.getParentId());
            Long rootId = resolveRootId(parent, commentMap);
            comment.setRootId(rootId);
            comment.setReplyToUserId(parent.getUserId());
            if (parent.getUserId() != null) {
                comment.setReplyToUser(userMapper.selectById(parent.getUserId()));
            }
            repliesByRoot.computeIfAbsent(rootId, ignored -> new ArrayList<>()).add(comment);
        }

        roots.forEach(root -> {
            List<Comment> replies = repliesByRoot.getOrDefault(root.getId(), new ArrayList<>());
            replies.sort(Comparator.comparing(Comment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            root.setReplies(replies);
            root.setReplyCount(replies.size());
        });

        return roots;
    }

    private Long resolveRootId(Comment comment, Map<Long, Comment> commentMap) {
        Comment current = comment;
        Set<Long> visited = new HashSet<>();
        while (current.getParentId() != null && commentMap.containsKey(current.getParentId()) && visited.add(current.getId())) {
            current = commentMap.get(current.getParentId());
        }
        return current.getId();
    }

    private void populateCommentUsers(List<Comment> comments, Long currentUserId) {
        comments.forEach(comment -> {
            comment.setCommentId(comment.getId());
            User user = userMapper.selectById(comment.getUserId());
            if (user != null) {
                comment.setUser(user);
            }

            if (currentUserId == null) {
                comment.setLiked(false);
                return;
            }

            QueryWrapper<CommentLike> likeQuery = new QueryWrapper<>();
            likeQuery.eq("comment_id", comment.getId()).eq("user_id", currentUserId);
            comment.setLiked(commentLikeMapper.selectCount(likeQuery) > 0);
        });
    }
}
