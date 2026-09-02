package com.example.video.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MinioService {
    private final MinioClient minioClient;

    @Value("${minio.bucket:hangyin-video}")
    private String bucket;

    @Value("${minio.public-base-url:http://localhost:8082/video}")
    private String publicBaseUrl;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            // 允许匿名只读，使上传后生成的播放地址可直接被浏览器/端到端测试访问
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy())
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("初始化 MinIO 存储桶失败: " + exception.getMessage(), exception);
        }
    }

    private String publicReadPolicy() {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
    }

    public void upload(Path file, String objectName, String contentType) {
        ensureBucket();
        try (InputStream inputStream = Files.newInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, Files.size(file), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("上传文件到 MinIO 失败: " + exception.getMessage(), exception);
        }
    }

    public void upload(byte[] data, String objectName, String contentType) {
        ensureBucket();
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName)
                    .stream(inputStream, data.length, -1).contentType(contentType).build());
        } catch (Exception exception) {
            throw new IllegalStateException("上传文件到 MinIO 失败: " + exception.getMessage(), exception);
        }
    }

    public String publicUrl(String objectName) {
        return publicBaseUrl.replaceAll("/+$", "") + "/" + objectName;
    }

    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception exception) {
            throw new IllegalStateException("删除 MinIO 文件失败: " + exception.getMessage(), exception);
        }
    }

    public Map<String, Object> testConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", publicBaseUrl);
        result.put("bucket", bucket);
        try {
            ensureBucket();
            result.put("connected", true);
        } catch (Exception exception) {
            result.put("connected", false);
            result.put("error", exception.getMessage());
        }
        return result;
    }
}
