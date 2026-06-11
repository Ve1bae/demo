package com.example.demo.vo;

import com.example.demo.entity.LiveRoom;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveRoomVO {
    private Long roomId;
    private Long userId;
    private Long categoryId;
    private String anchorNickname;
    private String title;
    private String streamName;
    private String pushUrl;
    private String pullUrl;
    private String coverUrl;
    private String status;
    private LocalDateTime createdAt;

    public static LiveRoomVO fromEntity(LiveRoom room) {
        LiveRoomVO vo = new LiveRoomVO();
        vo.setRoomId(room.getId());
        vo.setUserId(room.getUserId());
        vo.setCategoryId(room.getCategoryId());
        vo.setTitle(room.getTitle());
        vo.setStreamName(room.getStreamName());
        vo.setPushUrl(room.getPushUrl());
        vo.setPullUrl(room.getPlayUrl());
        vo.setCoverUrl(room.getCoverUrl());
        vo.setStatus(room.getStatus());
        vo.setCreatedAt(room.getCreateTime());
        return vo;
    }
}
