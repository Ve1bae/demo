package com.example.demo.controller;

import com.example.demo.client.UserServiceClient;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.Video;
import com.example.demo.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 故障处理实验专用接口
 * GET /api/videos/{videoId}/author-info
 *   - 调 user-service 取作者昵称
 *   - user-service 宕机时返回备用结果 "匿名用户", 不崩溃
 */
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class FaultTestController {

    private final VideoService videoService;
    private final UserServiceClient userServiceClient;

    @GetMapping("/{videoId}/author-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuthorInfo(@PathVariable Long videoId) {
        Video video = videoService.getVideoById(videoId);
        if (video == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "视频不存在"));
        }

        Long authorId = video.getUserId();
        // 跨服务调用: 超时3秒, 失败返回备用结果 "匿名用户"
        String nickname = userServiceClient.getUserNickname(authorId);

        Map<String, Object> result = new HashMap<>();
        result.put("videoId", videoId);
        result.put("title", video.getTitle());
        result.put("authorId", authorId);
        result.put("nickname", nickname);
        result.put("fallback", "匿名用户".equals(nickname));

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
