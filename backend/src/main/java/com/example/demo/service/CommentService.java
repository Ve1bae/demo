package com.example.demo.service;

import com.example.demo.entity.Comment;

import java.util.List;
import java.util.Map;

public interface CommentService {

    List<Comment> getCommentsByVideoId(Long videoId, Long currentUserId);

    Map<String, Object> getCommentsByVideoIdWithPagination(Long videoId, Integer page, Integer pageSize, Long currentUserId);

    Comment getCommentById(Long commentId);

    boolean saveComment(Comment comment);

    boolean deleteComment(Long id);

    Map<String, Object> likeComment(Long commentId, Long userId);
}
