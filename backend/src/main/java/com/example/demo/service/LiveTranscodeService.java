package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiveTranscodeService {

    private static final Logger log = LoggerFactory.getLogger(LiveTranscodeService.class);

    private final Map<Long, TranscodeWorker> workers = new ConcurrentHashMap<>();

    @Value("${live.transcode.enabled:true}")
    private boolean enabled;

    @Value("${live.transcode.ffmpeg-path:${video.transcode.ffmpeg-path:ffmpeg}}")
    private String ffmpegPath;

    @Value("${live.transcode.retry-interval-ms:5000}")
    private long retryIntervalMs;

    public void start(Long roomId, String inputRtmpUrl, String output480pUrl, String output720pUrl) {
        if (!enabled || roomId == null || inputRtmpUrl == null || inputRtmpUrl.isBlank()) {
            return;
        }
        stop(roomId);
        TranscodeWorker worker = new TranscodeWorker(roomId, inputRtmpUrl, output480pUrl, output720pUrl);
        workers.put(roomId, worker);
        Thread thread = new Thread(worker, "live-transcode-" + roomId);
        worker.thread = thread;
        thread.setDaemon(true);
        thread.start();
    }

    public void stop(Long roomId) {
        TranscodeWorker worker = workers.remove(roomId);
        if (worker != null) {
            worker.stop();
        }
    }

    @PreDestroy
    public void shutdown() {
        workers.keySet().forEach(this::stop);
    }

    private List<String> buildCommand(String inputRtmpUrl, String output480pUrl, String output720pUrl) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-i");
        command.add(inputRtmpUrl);

        addOutput(command, "854:480", "1000k", output480pUrl);
        addOutput(command, "1280:720", "2500k", output720pUrl);
        return command;
    }

    private void addOutput(List<String> command, String size, String videoBitrate, String outputUrl) {
        command.add("-map");
        command.add("0:v:0");
        command.add("-map");
        command.add("0:a?");
        command.add("-vf");
        command.add("scale=" + size + ":force_original_aspect_ratio=decrease,pad=" + size + ":(ow-iw)/2:(oh-ih)/2");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-tune");
        command.add("zerolatency");
        command.add("-b:v");
        command.add(videoBitrate);
        command.add("-maxrate");
        command.add(videoBitrate);
        command.add("-bufsize");
        command.add("2M");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-f");
        command.add("flv");
        command.add(outputUrl);
    }

    private class TranscodeWorker implements Runnable {
        private final Long roomId;
        private final String inputRtmpUrl;
        private final String output480pUrl;
        private final String output720pUrl;
        private volatile boolean running = true;
        private volatile Process process;
        private Thread thread;

        private TranscodeWorker(Long roomId, String inputRtmpUrl, String output480pUrl, String output720pUrl) {
            this.roomId = roomId;
            this.inputRtmpUrl = inputRtmpUrl;
            this.output480pUrl = output480pUrl;
            this.output720pUrl = output720pUrl;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    process = new ProcessBuilder(buildCommand(inputRtmpUrl, output480pUrl, output720pUrl))
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start();
                    int exitCode = process.waitFor();
                    if (running) {
                        log.warn("Live transcode process for room {} exited with code {}, retrying.", roomId, exitCode);
                        Thread.sleep(retryIntervalMs);
                    }
                } catch (IOException e) {
                    if (running) {
                        log.warn("Failed to start live transcode for room {}: {}", roomId, e.getMessage());
                        sleepBeforeRetry();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    process = null;
                }
            }
        }

        private void stop() {
            running = false;
            Process currentProcess = process;
            if (currentProcess != null) {
                currentProcess.destroy();
                if (currentProcess.isAlive()) {
                    currentProcess.destroyForcibly();
                }
            }
            if (thread != null) {
                thread.interrupt();
            }
        }

        private void sleepBeforeRetry() {
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
