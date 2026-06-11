package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.RoomLikes;
import com.example.demo.mapper.RoomLikesMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomLikesService {

    @Autowired
    private RoomLikesMapper roomLikesMapper;

    /**
     * 给直播间点赞，并返回最新的点赞总数
     * @param roomId 直播间ID
     * @return 点赞后的总数
     */
    @Transactional
    public long addLike(String roomId) {
        // 先查询是否存在
        RoomLikes likes = roomLikesMapper.selectById(roomId);
        if (likes == null) {
            likes = new RoomLikes();
            likes.setRoomId(roomId);
            likes.setLikeCount(1L);
            roomLikesMapper.insert(likes);
            return 1L;
        } else {
            likes.setLikeCount(likes.getLikeCount() + 1);
            roomLikesMapper.updateById(likes);
            return likes.getLikeCount();
        }
    }

    /**
     * 获取直播间的点赞数
     * @param roomId 直播间ID
     * @return 点赞数
     */
    public long getLikeCount(String roomId) {
        RoomLikes likes = roomLikesMapper.selectById(roomId);
        return likes == null ? 0L : likes.getLikeCount();
    }
}