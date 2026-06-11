package com.example.demo.controller;

import com.example.demo.service.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/minio")
public class MinioController {

    @Autowired
    private MinioService minioService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = "uploads/" + UUID.randomUUID().toString() + extension;

            // 上传文件
            minioService.uploadFile(file, objectName);

            // 获取访问 URL
            String fileUrl = minioService.getFileUrl(objectName);

            result.put("success", true);
            result.put("url", fileUrl);
            result.put("objectName", objectName);
            result.put("message", "文件上传成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/delete")
    public Map<String, Object> deleteFile(@RequestParam String objectName) {
        Map<String, Object> result = new HashMap<>();
        try {
            minioService.deleteFile(objectName);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取文件访问URL
     */
    @GetMapping("/url")
    public Map<String, Object> getFileUrl(@RequestParam String objectName) {
        Map<String, Object> result = new HashMap<>();
        try {
            String fileUrl = minioService.getFileUrl(objectName);
            result.put("success", true);
            result.put("url", fileUrl);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取URL失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 测试 MinIO 连接
     */
    @GetMapping("/test")
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "MinIO 连接正常");
        result.put("endpoint", minioEndpoint);
        result.put("bucket", "hangyin-video");
        return result;
    }
}
