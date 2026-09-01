package com.example.demo.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 跨服务调用 user-service 的 HTTP 客户端
 * 实现: 超时返回 + 备用结果 (故障隔离)
 * - 写操作(验证用户): 超时/失败 → 返回 false (强校验, 拒绝写入)
 * - 读操作(取昵称): 超时/失败 → 返回 "匿名用户" (降级, 不阻塞)
 */
@Service
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${user-service.base-url:http://localhost:8081}")
    private String baseUrl;

    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 写操作前验证用户是否存在 (强校验)
     * user-service 不可用时返回 false, 调用方应拒绝该操作
     */
    public boolean verifyUserExists(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            restTemplate.getForEntity(baseUrl + "/api/users/" + userId, Object.class);
            return true;
        } catch (Exception e) {
            // 超时或连接失败: 备用结果 = false
            return false;
        }
    }

    /**
     * 读操作: 获取用户昵称 (可降级)
     * user-service 不可用时返回 "匿名用户", 不阻塞调用方
     */
    public String getUserNickname(Long userId) {
        if (userId == null) {
            return "匿名用户";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    baseUrl + "/api/users/" + userId, Map.class);
            if (response != null && response.get("data") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                Object nickname = data.get("nickname");
                if (nickname != null) {
                    return nickname.toString();
                }
            }
            return "匿名用户";
        } catch (Exception e) {
            // 超时或连接失败: 备用结果 = "匿名用户"
            return "匿名用户";
        }
    }
}
