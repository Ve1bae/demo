package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.RoomLikes;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RoomLikesMapper extends BaseMapper<RoomLikes> {

    @Insert("INSERT INTO room_likes(room_id, like_count) VALUES(#{roomId}, 1) ON DUPLICATE KEY UPDATE like_count = like_count + 1")
    void upsertIncrement(@Param("roomId") Long roomId);

    @Update("UPDATE room_likes SET like_count = like_count + 1 WHERE room_id = #{roomId}")
    int increment(@Param("roomId") Long roomId);
}
