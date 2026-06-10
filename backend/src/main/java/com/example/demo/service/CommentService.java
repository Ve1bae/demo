package com.example.demo.service;

import com.example.demo.entity.Comment;
import java.util.List;
import java.util.Map;

public interface CommentService {
    
    List<Comment> getCommentsByVideoId(Long videoId);
    
    Map<String, Object> getCommentsByVideoIdWithPagination(Long videoId, Integer page, Integer pageSize);
    
    boolean saveComment(Comment comment);
    
    boolean deleteComment(Long id);
    
    boolean likeComment(Long commentId);
}