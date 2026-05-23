package com.bytedance.aivideo.engine.ffmpeg.local;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.FfmpegAudioExtractProperties;
import com.bytedance.aivideo.engine.ffmpeg.api.AudioExtractEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地 FFmpeg 音轨提取实现。
 */
@Component
@Slf4j
public class LocalFfmpegAudioExtractEngine implements AudioExtractEngine {

    private final FfmpegAudioExtractProperties ffmpegAudioExtractProperties;

    public LocalFfmpegAudioExtractEngine(FfmpegAudioExtractProperties ffmpegAudioExtractProperties) {
        this.ffmpegAudioExtractProperties = ffmpegAudioExtractProperties;
    }

    @Override
    public Path extractToMp3(Path videoPath, Path outputDir, String outputName) {
        if (videoPath == null) {
            throw new BizException(ErrorCode.AUDIO_EXTRACT_ERROR, "视频路径不能为空");
        }
        if (outputDir == null) {
            throw new BizException(ErrorCode.AUDIO_EXTRACT_ERROR, "音频输出目录不能为空");
        }
        if (outputName == null || outputName.isBlank()) {
            throw new BizException(ErrorCode.AUDIO_EXTRACT_ERROR, "音频输出文件名不能为空");
        }
        try {
            Files.createDirectories(outputDir);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.AUDIO_EXTRACT_ERROR, "创建音频目录失败: " + ex.getMessage());
        }

        Path outputPath = outputDir.resolve(outputName).normalize().toAbsolutePath();
        List<String> command = new ArrayList<>();
        command.add(ffmpegAudioExtractProperties.getPath());
        command.add("-y");
        command.add("-i");
        command.add(videoPath.toAbsolutePath().toString());
        command.add("-vn");
        command.add("-acodec");
        command.add("libmp3lame");
        command.add("-ar");
        command.add("16000");
        command.add("-ac");
        command.add("1");
        command.add(outputPath.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        log.info("ffmpeg audio extract start: videoPath={}, outputPath={}", videoPath.toAbsolutePath(), outputPath);
        long startMs = System.currentTimeMillis();

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(
                    Math.max(5, ffmpegAudioExtractProperties.getAudioTimeoutSeconds()),
                    TimeUnit.SECONDS
            );
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                log.error("ffmpeg audio extract timeout: videoPath={}, timeoutSec={}, output={}",
                        videoPath.toAbsolutePath(), ffmpegAudioExtractProperties.getAudioTimeoutSeconds(), processOutput);
                throw new BizException(
                        ErrorCode.AUDIO_EXTRACT_ERROR,
                        "音轨提取超时: " + Duration.ofSeconds(ffmpegAudioExtractProperties.getAudioTimeoutSeconds())
                );
            }
            if (process.exitValue() != 0) {
                log.error("ffmpeg audio extract failed: videoPath={}, exitCode={}, output={}",
                        videoPath.toAbsolutePath(), process.exitValue(), processOutput);
                throw new BizException(ErrorCode.AUDIO_EXTRACT_ERROR, "音轨提取失败: " + processOutput);
            }
            log.info("ffmpeg audio extract finished: videoPath={}, outputPath={}, elapsedMs={}",
                    videoPath.toAbsolutePath(), outputPath, System.currentTimeMillis() - startMs);
            return outputPath;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.AUDIO_EXTRACT_ERROR, "调用 ffmpeg 失败: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AUDIO_EXTRACT_ERROR, "音轨提取被中断: " + ex.getMessage());
        }
    }
}

