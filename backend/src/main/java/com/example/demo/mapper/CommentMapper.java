package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    
    List<Comment> selectByVideoId(@Param("videoId") Long videoId);
    
    List<Comment> selectByVideoIdWithPagination(
            @Param("videoId") Long videoId,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);
    
    Integer countByVideoId(@Param("videoId") Long videoId);
}