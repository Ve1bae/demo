package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Select("SELECT * FROM comment WHERE video_id = #{videoId} ORDER BY created_at DESC")
    List<Comment> selectByVideoId(@Param("videoId") Long videoId);

    @Select("SELECT * FROM comment WHERE video_id = #{videoId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<Comment> selectByVideoIdWithPagination(
            @Param("videoId") Long videoId,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);

    @Select("SELECT COUNT(*) FROM comment WHERE video_id = #{videoId}")
    Integer countByVideoId(@Param("videoId") Long videoId);
}
