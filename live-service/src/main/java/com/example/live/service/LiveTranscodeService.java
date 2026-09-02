package com.example.live.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 将主播原始 RTMP 流实时转成 480P/720P，并重新推回 SRS。 */
@Service
public class LiveTranscodeService {
    private final Map<Long, Process> workers = new ConcurrentHashMap<>();
    @Value("${live.transcode.enabled:true}") private boolean enabled;
    @Value("${live.transcode.ffmpeg-path:ffmpeg}") private String ffmpeg;
    @Value("${live.transcode.retry-interval-ms:5000}") private long retryMs;

    public void start(Long roomId, String input, String out480, String out720) {
        if (!enabled || roomId == null) return;
        stop(roomId);
        Thread t = new Thread(() -> run(roomId, input, out480, out720), "live-transcode-" + roomId);
        t.setDaemon(true); t.start();
    }
    private void run(Long id, String input, String out480, String out720) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Process p = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "warning", "-i", input,
                        "-map", "0:v:0", "-map", "0:a?", "-vf", "scale=854:480:force_original_aspect_ratio=decrease,pad=854:480:(ow-iw)/2:(oh-ih)/2", "-c:v", "libx264", "-preset", "veryfast", "-tune", "zerolatency", "-b:v", "1000k", "-c:a", "aac", "-f", "flv", out480,
                        "-map", "0:v:0", "-map", "0:a?", "-vf", "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2", "-c:v", "libx264", "-preset", "veryfast", "-tune", "zerolatency", "-b:v", "2500k", "-c:a", "aac", "-f", "flv", out720)
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                workers.put(id, p); p.waitFor(); workers.remove(id);
                Thread.sleep(retryMs);
            } catch (Exception e) { try { Thread.sleep(retryMs); } catch (InterruptedException x) { Thread.currentThread().interrupt(); } }
        }
    }
    public void stop(Long id) { Process p = workers.remove(id); if (p != null) p.destroyForcibly(); }
    @PreDestroy public void shutdown() { workers.keySet().forEach(this::stop); }
}
