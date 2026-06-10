package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Comment;
import com.example.demo.entity.User;
import com.example.demo.mapper.CommentMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.CommentService;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final UserMapper userMapper;

    public CommentServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<Comment> getCommentsByVideoId(Long videoId) {
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("video_id", videoId);
        queryWrapper.orderByDesc("created_at");
        List<Comment> comments = baseMapper.selectList(queryWrapper);
        
        // 填充用户信息
        comments.forEach(comment -> {
            comment.setCommentId(comment.getId());
            User user = userMapper.selectById(comment.getUserId());
            if (user != null) {
                comment.setUser(user);
            }
        });
        
        return comments;
    }

    @Override
    public Map<String, Object> getCommentsByVideoIdWithPagination(Long videoId, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<Comment> comments = baseMapper.selectByVideoIdWithPagination(videoId, offset, pageSize);
        Integer total = baseMapper.countByVideoId(videoId);
        
        // 填充用户信息和commentId
        comments.forEach(comment -> {
            comment.setCommentId(comment.getId());
            User user = userMapper.selectById(comment.getUserId());
            if (user != null) {
                comment.setUser(user);
            }
        });
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", comments);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        
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
    public boolean likeComment(Long commentId) {
        Comment comment = getById(commentId);
        if (comment != null) {
            comment.setLikeCount(comment.getLikeCount() + 1);
            return updateById(comment);
        }
        return false;
    }
}