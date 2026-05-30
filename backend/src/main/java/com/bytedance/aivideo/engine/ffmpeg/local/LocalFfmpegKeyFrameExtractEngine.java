package com.bytedance.aivideo.engine.ffmpeg.local;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.FfmpegCommandProperties;
import com.bytedance.aivideo.engine.ffmpeg.api.KeyFrameExtractEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 本地 FFmpeg 关键帧抽取引擎实现。
 */
@Component
@Slf4j
public class LocalFfmpegKeyFrameExtractEngine implements KeyFrameExtractEngine {

    private final FfmpegCommandProperties ffmpegProperties;

    public LocalFfmpegKeyFrameExtractEngine(FfmpegCommandProperties ffmpegProperties) {
        this.ffmpegProperties = ffmpegProperties;
    }

    @Async("videoTaskExecutor")
    @Override
    public CompletableFuture<List<Path>> extractFramesAsync(Path videoPath, List<Double> timestamps, Path outputDir) {
        try {
            return CompletableFuture.completedFuture(doExtractFrames(videoPath, timestamps, outputDir));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private List<Path> doExtractFrames(Path videoPath, List<Double> timestamps, Path outputDir) {
        if (timestamps == null || timestamps.isEmpty()) {
            return List.of();
        }

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "无法创建抽帧输出目录: " + outputDir);
        }

        List<Path> extractedPaths = new ArrayList<>();
        long startMs = System.currentTimeMillis();

        for (int i = 0; i < timestamps.size(); i++) {
            double targetTime = timestamps.get(i);
            // 确保文件名从 1 开始
            String fileName = String.format("frame_%03d.jpg", i + 1);
            Path outputPath = outputDir.resolve(fileName).normalize();

            // ffmpeg -ss {targetTime} -i input.mp4 -an -frames:v 1 -q:v 2 frame_{index}.jpg
            List<String> command = new ArrayList<>();
            command.add(ffmpegProperties.getPath());
            command.add("-y"); // 全局参数：直接覆盖已有文件，避免交互式挂起或报错
            command.add("-hide_banner");
            command.add("-loglevel");
            command.add("error");
            command.add("-ss");
            command.add(String.format(java.util.Locale.US, "%.3f", targetTime));
            command.add("-i");
            command.add(videoPath.toAbsolutePath().toString());
            command.add("-an");
            command.add("-frames:v");
            command.add("1");
            command.add("-q:v");
            command.add("2");
            command.add(outputPath.toAbsolutePath().toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            // 合并标准错误到标准输出
            processBuilder.redirectErrorStream(true);

            Process process = null;
            try {
                process = processBuilder.start();
                
                // 读取输出
                String processOutput = new String(process.getInputStream().readAllBytes());

                boolean finished = process.waitFor(15, TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    log.warn("ffmpeg extract frame timeout: time={}", targetTime);
                    continue;
                }
                if (process.exitValue() != 0) {
                    log.warn("ffmpeg extract frame failed: exitValue={}, time={}, output={}", 
                             process.exitValue(), targetTime, processOutput);
                    continue;
                }

                if (Files.exists(outputPath)) {
                    extractedPaths.add(outputPath);
                }
            } catch (IOException | InterruptedException e) {
                if (process != null) {
                    process.destroyForcibly();
                }
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("ffmpeg extract frame error: time={}, msg={}", targetTime, e.getMessage());
            }
        }

        log.info("ffmpeg extract frames finished: totalReq={}, success={}, elapsedMs={}", 
                timestamps.size(), extractedPaths.size(), System.currentTimeMillis() - startMs);

        return extractedPaths;
    }
}
