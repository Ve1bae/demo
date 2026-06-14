package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.entity.Danmaku;
import com.example.demo.entity.Video;
import com.example.demo.service.DanmakuService;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class DanmakuController {

    private final DanmakuService danmakuService;
    private final VideoService videoService;

    @GetMapping("/{videoId}/danmakus")
    public ResponseEntity<ApiResponse<List<Danmaku>>> getDanmakuByVideoId(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "0") Double startTime,
            @RequestParam(defaultValue = "60") Double endTime) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        List<Danmaku> danmakuList = danmakuService.getDanmakuByVideoUrl(video.getVideoUrl()).stream()
                .filter(d -> d.getTime() >= startTime && d.getTime() <= endTime)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(danmakuList));
    }

    @PostMapping("/{videoId}/danmakus")
    public ResponseEntity<ApiResponse<String>> sendDanmaku(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> requestBody) {

        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Danmaku danmaku = new Danmaku();
        danmaku.setVideoUrl(video.getVideoUrl());
        danmaku.setContent((String) requestBody.get("content"));

        Object timeSeconds = requestBody.get("timeSeconds");
        if (timeSeconds instanceof Number number) {
            danmaku.setTime(number.doubleValue());
        } else if (timeSeconds instanceof String timeString) {
            danmaku.setTime(Double.parseDouble(timeString));
        }

        Object color = requestBody.get("color");
        if (color != null) {
            danmaku.setColor(color.toString());
        }

        Object userId = requestBody.get("userId");
        danmaku.setUserId(userId != null ? userId.toString() : "anonymous");
        danmaku.setIsUser(false);

        boolean success = danmakuService.saveDanmaku(danmaku);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("弹幕发送成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error(500, "弹幕发送失败"));
    }

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
        }
        return ResponseEntity.ok(ApiResponse.error(500, "弹幕发送失败"));
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
        }
        return ResponseEntity.ok(ApiResponse.error(500, "弹幕删除失败"));
    }
}
