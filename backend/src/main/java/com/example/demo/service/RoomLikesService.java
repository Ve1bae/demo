package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.RoomLikes;
import com.example.demo.mapper.RoomLikesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomLikesService extends ServiceImpl<RoomLikesMapper, RoomLikes> {

    @Transactional
    public long addLike(Long roomId) {
        baseMapper.upsertIncrement(roomId);
        return getLikeCount(roomId);
    }

    public long getLikeCount(Long roomId) {
        RoomLikes likes = getById(roomId);
        return likes == null || likes.getLikeCount() == null ? 0L : likes.getLikeCount();
    }

    public void resetLikeCount(Long roomId) {
        if (roomId == null) {
            return;
        }
        RoomLikes likes = new RoomLikes();
        likes.setRoomId(roomId);
        likes.setLikeCount(0L);
        saveOrUpdate(likes);
    }}
