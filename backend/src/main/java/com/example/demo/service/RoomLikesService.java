package com.example.demo.service;

import com.example.demo.entity.RoomLikes;
import com.example.demo.mapper.RoomLikesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomLikesService {
    private final RoomLikesMapper roomLikesMapper;

    public RoomLikesService(RoomLikesMapper roomLikesMapper) {
        this.roomLikesMapper = roomLikesMapper;
    }

    @Transactional
    public long addLike(String roomId) {
        RoomLikes likes = roomLikesMapper.selectById(roomId);
        if (likes == null) {
            likes = new RoomLikes();
            likes.setRoomId(roomId);
            likes.setLikeCount(1L);
            roomLikesMapper.insert(likes);
            return 1L;
        }
        likes.setLikeCount((likes.getLikeCount() == null ? 0L : likes.getLikeCount()) + 1);
        roomLikesMapper.updateById(likes);
        return likes.getLikeCount();
    }

    public long getLikeCount(String roomId) {
        RoomLikes likes = roomLikesMapper.selectById(roomId);
        return likes == null || likes.getLikeCount() == null ? 0L : likes.getLikeCount();
    }
}
