package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.UserVideo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserVideoMapper extends BaseMapper<UserVideo> {

    @Select("SELECT * FROM user_video WHERE user_id = #{userId} AND video_id = #{videoId}")
    UserVideo findByUserIdAndVideoId(@Param("userId") Long userId, @Param("videoId") Long videoId);

    @Update("UPDATE video SET like_count = like_count + #{delta} WHERE id = #{videoId}")
    void updateVideoLikeCount(@Param("videoId") Long videoId, @Param("delta") Integer delta);

    @Update("UPDATE video SET favorite_count = favorite_count + #{delta} WHERE id = #{videoId}")
    void updateVideoFavoriteCount(@Param("videoId") Long videoId, @Param("delta") Integer delta);

    @Update("UPDATE video SET play_count = play_count + 1 WHERE id = #{videoId}")
    void incrementPlayCount(@Param("videoId") Long videoId);
}
