package com.example.demo.service;

import com.example.demo.entity.Comment;
import java.util.List;
import java.util.Map;

public interface CommentService {

    List<Comment> getCommentsByVideoId(Long videoId);

    Comment getCommentById(Long commentId);

    Map<String, Object> getCommentsByVideoIdWithPagination(Long videoId, Integer page, Integer pageSize);

    Map<String, Object> getCommentsByVideoIdWithPagination(Long videoId, Integer page, Integer pageSize, Long viewerId);

    boolean saveComment(Comment comment);

    boolean deleteComment(Long id);

    boolean likeComment(Long commentId, Long userId);

    boolean unlikeComment(Long commentId, Long userId);
}
