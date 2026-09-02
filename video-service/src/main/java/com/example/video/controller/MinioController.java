package com.example.video.controller;

import com.example.video.common.ApiResponse;
import com.example.video.service.MinioService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/minio")
public class MinioController {
    private final MinioService minio;
    public MinioController(MinioService minio) { this.minio = minio; }

    @GetMapping("/test") public ApiResponse<Map<String, Object>> test() { return ApiResponse.success(minio.testConnection()); }

    @PostMapping("/upload") public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的文件");
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        int index = original.lastIndexOf('.');
        String ext = index >= 0 ? original.substring(index) : "";
        String objectName = "uploads/" + UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            java.nio.file.Path temp = java.nio.file.Files.createTempFile("minio-upload-", ext);
            file.transferTo(temp);
            minio.upload(temp, objectName, file.getContentType());
            java.nio.file.Files.deleteIfExists(temp);
        } catch (Exception exception) {
            throw new IllegalStateException("上传文件失败: " + exception.getMessage(), exception);
        }
        return ApiResponse.success("上传成功", Map.of("objectName", objectName, "url", minio.publicUrl(objectName)));
    }

    @GetMapping("/url") public ApiResponse<Map<String, Object>> url(@RequestParam String objectName) { return ApiResponse.success(Map.of("url", minio.publicUrl(objectName))); }

    @DeleteMapping("/delete") public ApiResponse<Map<String, Object>> delete(@RequestParam String objectName) { minio.delete(objectName); return ApiResponse.success("删除成功", Map.of("objectName", objectName)); }
}
