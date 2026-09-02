package com.example.video.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class VideoTranscodeService {
    private static final String[][] QUALITY_PLANS = {
            {"480P", "854:480"},
            {"720P", "1280:720"},
            {"1080P", "1920:1080"}
    };

    private final MinioService minioService;

    @Value("${video.transcode.enabled:false}")
    private boolean enabled;

    @Value("${video.transcode.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${video.transcode.timeout-seconds:300}")
    private long timeoutSeconds;

    public VideoTranscodeService(MinioService minioService) {
        this.minioService = minioService;
    }

    /**
     * 用 FFmpeg 把源视频转成多个清晰度并上传 MinIO。
     * 返回清晰度 -> objectName 的映射；转码未启用或失败时返回空 Map（调用方回退到原始文件）。
     */
    public Map<String, String> transcodeAndUpload(Path source, String objectPrefix) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!enabled || source == null) return result;
        for (String[] plan : QUALITY_PLANS) {
            String quality = plan[0];
            String scale = plan[1];
            Path output = null;
            try {
                output = Files.createTempFile("transcode-" + quality + "-", ".mp4");
                boolean finished = runFfmpeg(source, output, scale);
                if (!finished || Files.size(output) == 0) {
                    continue;
                }
                String objectName = objectPrefix + "/video-" + UUID.randomUUID().toString().replace("-", "") + "-" + quality + ".mp4";
                minioService.upload(output, objectName, "video/mp4");
                result.put(quality, objectName);
            } catch (Exception ignored) {
                // 单档转码失败不影响其他清晰度，也不阻断原始文件上传
            } finally {
                deleteQuietly(output);
            }
        }
        return result;
    }

    private boolean runFfmpeg(Path source, Path output, String scale) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                ffmpegPath, "-i", source.toString(),
                "-vf", "scale=" + scale,
                "-c:v", "libx264", "-preset", "veryfast",
                "-c:a", "aac", "-y", output.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        // 持续排空子进程输出，避免缓冲区写满导致 ffmpeg 卡死
        process.getInputStream().transferTo(OutputStream.nullOutputStream());
        return process.waitFor(timeoutSeconds, TimeUnit.SECONDS) && process.exitValue() == 0;
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 忽略清理失败
        }
    }
}
