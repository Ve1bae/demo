package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.PageResult;
import com.example.demo.dto.LiveRoomCreateDTO;
import com.example.demo.service.LiveRoomService;
import com.example.demo.vo.LiveRoomVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live/rooms")
public class LiveRoomController {
    @Autowired
    private LiveRoomService liveRoomService;

    @GetMapping
    public ApiResponse<PageResult<LiveRoomVO>> listRooms(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "12") Long pageSize,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(liveRoomService.listRooms(page, pageSize, categoryId));
    }

    @PostMapping
    public ApiResponse<LiveRoomVO> createRoom(
            @RequestBody LiveRoomCreateDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        try {
            return ApiResponse.success(liveRoomService.createRoom(dto, userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @GetMapping("/{roomId}")
    public ApiResponse<LiveRoomVO> getRoom(@PathVariable Long roomId) {
        try {
            return ApiResponse.success(liveRoomService.getRoom(roomId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    @PostMapping("/{roomId}/close")
    public ApiResponse<LiveRoomVO> closeRoom(
            @PathVariable Long roomId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        try {
            return ApiResponse.success(liveRoomService.closeRoom(roomId, userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }
}
