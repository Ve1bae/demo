package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.Danmaku;
import com.example.demo.entity.Video;
import com.example.demo.service.DanmakuService;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class DanmakuController {
    
    private final DanmakuService danmakuService;
    private final VideoService videoService;
    
    /**
     * 获取视频弹幕列表
     * GET /api/videos/{videoId}/danmakus?startTime=0&endTime=60
     */
    @GetMapping("/{videoId}/danmakus")
    public ResponseEntity<ApiResponse<List<Danmaku>>> getDanmakuByVideoId(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "0") Double startTime,
            @RequestParam(defaultValue = "60") Double endTime) {
        
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        
        // 使用 playUrl 作为弹幕关联字段
        String videoIdentifier = video.getPlayUrl();
        List<Danmaku> danmakuList = danmakuService.getDanmakuByVideoUrl(videoIdentifier);
        
        if (startTime != null || endTime != null) {
            danmakuList = danmakuList.stream()
                    .filter(d -> d.getTime() >= startTime && d.getTime() <= endTime)
                    .toList();
        }
        
        return ResponseEntity.ok(ApiResponse.success(danmakuList));
    }
    
    /**
     * 发送弹幕
     * POST /api/videos/{videoId}/danmakus
     * 请求体：{ "content": "弹幕内容", "timeSeconds": 15, "color": "#ffffff" }
     */
    @PostMapping("/{videoId}/danmakus")
    public ResponseEntity<ApiResponse<String>> sendDanmaku(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {
        
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }
        
        // 使用 playUrl 作为弹幕关联字段
        String videoIdentifier = video.getPlayUrl();
        
        Danmaku danmaku = new Danmaku();
        danmaku.setVideoUrl(videoIdentifier);
        danmaku.setContent((String) requestBody.get("content"));
        
        // 支持 API 规范中的 timeSeconds 字段
        Object timeSeconds = requestBody.get("timeSeconds");
        if (timeSeconds instanceof Number) {
            danmaku.setTime(((Number) timeSeconds).doubleValue());
        } else if (timeSeconds instanceof String) {
            danmaku.setTime(Double.parseDouble((String) timeSeconds));
        }
        
        // 支持 color 字段（十六进制颜色）
        Object color = requestBody.get("color");
        if (color != null) {
            danmaku.setColor(color.toString());
        }
        
        // 支持 userId 字段（可选）
        Object userId = requestBody.get("userId");
        if (userId != null) {
            danmaku.setUserId(userId.toString());
        } else {
            danmaku.setUserId("anonymous");
        }
        
        danmaku.setIsUser(false);
        
        boolean success = danmakuService.saveDanmaku(danmaku);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("弹幕发送成功"));
        } else {
            return ResponseEntity.ok(ApiResponse.error(500, "弹幕发送失败"));
        }
    }
    
    /**
     * 保留旧接口用于兼容
     */
    @GetMapping("/danmaku/video")
    public ResponseEntity<ApiResponse<List<Danmaku>>> getDanmakuByVideoUrl(@RequestParam String videoUrl) {
        List<Danmaku> danmakuList = danmakuService.getDanmakuByVideoUrl(videoUrl);
        return ResponseEntity.ok(ApiResponse.success(danmakuList));
    }
    
    @PostMapping("/danmaku")
    public ResponseEntity<ApiResponse<String>> saveDanmakuOld(@RequestBody Danmaku danmaku) {
        boolean success = danmakuService.saveDanmaku(danmaku);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("弹幕发送成功"));
        } else {
            return ResponseEntity.ok(ApiResponse.error(500, "弹幕发送失败"));
        }
    }
    
    @GetMapping("/danmaku/user")
    public ResponseEntity<ApiResponse<List<Danmaku>>> getDanmakuByUserId(@RequestParam String userId) {
        List<Danmaku> danmakuList = danmakuService.getDanmakuByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(danmakuList));
    }
    
    @DeleteMapping("/danmaku/{id}")
    public ResponseEntity<ApiResponse<String>> deleteDanmaku(@PathVariable Long id) {
        boolean success = danmakuService.deleteDanmaku(id);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("弹幕删除成功"));
        } else {
            return ResponseEntity.ok(ApiResponse.error(500, "弹幕删除失败"));
        }
    }
}
