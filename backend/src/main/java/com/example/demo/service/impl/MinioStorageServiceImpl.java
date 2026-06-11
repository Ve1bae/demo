package com.example.demo.service.impl;

import com.example.demo.dto.ChunkInitResult;
import com.example.demo.dto.UploadProgress;
import com.example.demo.service.MinioStorageService;
import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageServiceImpl implements MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageServiceImpl.class);

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${video.chunk-dir:C:/hangyin/chunks}")
    private String chunkDir;

    /** objectName -> uploadId，用于追踪进行中的分片上传 */
    private final ConcurrentHashMap<String, String> uploadIdMap = new ConcurrentHashMap<>();

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket();
    }

    // ==================== 分片上传 ====================

    @Override
    public ChunkInitResult initChunkUpload(String objectName, String contentType) {
        try {
            Files.createDirectories(Path.of(chunkDir));

            // 生成唯一的 uploadId
            String uploadId = uploadIdMap.computeIfAbsent(objectName,
                    k -> UUID.randomUUID().toString().replace("-", ""));

            // 检查已上传的分片（断点续传）
            List<Integer> uploadedParts = listUploadedParts(objectName, uploadId);

            ChunkInitResult result = new ChunkInitResult();
            result.setUploadId(uploadId);
            result.setUploadedParts(uploadedParts);
            result.setObjectName(objectName);
            result.setCreated(uploadedParts.isEmpty());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("初始化分片上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void uploadChunk(String objectName, String uploadId, int partNumber,
                            InputStream data, long size, String contentType) {
        try {
            Path partDir = Path.of(chunkDir, uploadId);
            Files.createDirectories(partDir);
            Path partFile = partDir.resolve(String.format("part_%05d", partNumber));
            Files.copy(data, partFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("分片上传失败: part=" + partNumber + ", " + e.getMessage(), e);
        }
    }

    @Override
    public String completeChunkUpload(String objectName, String uploadId,
                                      List<Integer> sortedPartNumbers) {
        try {
            Path partDir = Path.of(chunkDir, uploadId);
            Path mergedFile = Path.of(chunkDir, uploadId + ".merged");
            Files.createDirectories(mergedFile.getParent());

            // 1. 按顺序合并所有分片
            try (OutputStream out = Files.newOutputStream(mergedFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int partNumber : sortedPartNumbers) {
                    Path partFile = partDir.resolve(String.format("part_%05d", partNumber));
                    if (!Files.exists(partFile)) {
                        throw new RuntimeException("分片文件丢失: part " + partNumber);
                    }
                    Files.copy(partFile, out);
                }
            }

            // 2. 使用 MinIO SDK 上传到 MinIO（带认证）
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(Files.newInputStream(mergedFile), Files.size(mergedFile), -1)
                    .build());

            // 3. 清理本地临时文件
            deleteDirectory(partDir);
            Files.deleteIfExists(mergedFile);
            uploadIdMap.remove(objectName);

            return endpoint + "/" + bucket + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("合并分片失败: " + e.getMessage(), e);
        }
    }

    // ==================== 断点续传 ====================

    @Override
    public UploadProgress getUploadProgress(String objectName) {
        String uploadId = uploadIdMap.get(objectName);
        if (uploadId == null) return null;
        List<Integer> uploaded = listUploadedParts(objectName, uploadId);
        return new UploadProgress(uploadId, uploaded, 0);
    }

    // ==================== 小文件直传 ====================

    @Override
    public String uploadSmallFile(String objectName, InputStream data, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
            return endpoint + "/" + bucket + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("上传失败: " + e.getMessage(), e);
        }
    }

    // ==================== URL & 删除 ====================

    @Override
    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build());
        } catch (Exception e) {
            return endpoint + "/" + bucket + "/" + objectName;
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.warn("删除 MinIO 对象失败: object={}, {}", objectName, e.getMessage());
        }
    }

    // ==================== 私有 ====================

    /** 确保 MinIO bucket 存在 */
    private void ensureBucket() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("创建 MinIO bucket: {}", bucket);
            } else {
                log.info("MinIO bucket 已存在: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket 检查失败: {}", e.getMessage());
        }
    }

    /** 列出某个 uploadId 下已上传的分片号 */
    private List<Integer> listUploadedParts(String objectName, String uploadId) {
        List<Integer> parts = new ArrayList<>();
        Path partDir = Path.of(chunkDir, uploadId);
        if (!Files.exists(partDir)) return parts;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(partDir, "part_*")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                int num = Integer.parseInt(name.substring(5));
                parts.add(num);
            }
        } catch (Exception ignored) {}

        Collections.sort(parts);
        return parts;
    }

    /** 删除目录及其所有内容 */
    private void deleteDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path p : stream) Files.deleteIfExists(p);
                }
                Files.deleteIfExists(dir);
            }
        } catch (Exception ignored) {}
    }
}
