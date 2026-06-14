package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VideoTranscodeService {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeService.class);
    private static final List<QualityProfile> QUALITY_PROFILES = List.of(
            new QualityProfile("480P", 480),
            new QualityProfile("720P", 720),
            new QualityProfile("1080P", 1080)
    );

    private final MinioService minioService;

    @Value("${video.transcode.enabled:true}")
    private boolean transcodeEnabled;

    @Value("${video.transcode.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${video.transcode.timeout-seconds:300}")
    private long timeoutSeconds;

    public Map<String, String> transcodeAndUpload(Path sourceFile, String objectPrefix) {
        Map<String, String> qualityObjectNames = new LinkedHashMap<>();
        if (!transcodeEnabled) {
            return qualityObjectNames;
        }

        for (QualityProfile profile : QUALITY_PROFILES) {
            Path outputFile = null;
            try {
                outputFile = Files.createTempFile("video-" + profile.label().toLowerCase() + "-", ".mp4");
                boolean success = transcode(sourceFile, outputFile, profile.height());
                if (!success || !Files.exists(outputFile) || Files.size(outputFile) == 0) {
                    continue;
                }
                String objectName = objectPrefix + "/" + profile.label().toLowerCase() + "-" + UUID.randomUUID() + ".mp4";
                minioService.uploadLocalFile(outputFile, objectName, "video/mp4");
                qualityObjectNames.put(profile.label(), objectName);
            } catch (Exception e) {
                log.warn("Skip {} transcoding: {}", profile.label(), e.getMessage());
            } finally {
                deleteQuietly(outputFile);
            }
        }
        return qualityObjectNames;
    }

    private boolean transcode(Path sourceFile, Path outputFile, int height) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-i", sourceFile.toAbsolutePath().toString(),
                "-vf", "scale=-2:" + height,
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "23",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                outputFile.toAbsolutePath().toString()
        )
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("FFmpeg transcoding timed out for {}P", height);
            return false;
        }
        if (process.exitValue() != 0) {
            log.warn("FFmpeg exited with code {} for {}P", process.exitValue(), height);
            return false;
        }
        return true;
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private record QualityProfile(String label, int height) {
    }
}
