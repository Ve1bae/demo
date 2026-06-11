package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.common.PageResult;
import com.example.demo.dto.LiveRoomCreateDTO;
import com.example.demo.entity.LiveRoom;
import com.example.demo.vo.LiveRoomVO;

public interface LiveRoomService extends IService<LiveRoom> {
    PageResult<LiveRoomVO> listRooms(Long page, Long pageSize, Long categoryId);

    LiveRoomVO createRoom(LiveRoomCreateDTO dto, Long headerUserId);

    LiveRoomVO getRoom(Long roomId);

    LiveRoomVO closeRoom(Long roomId, Long operatorUserId);
}
