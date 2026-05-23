package com.bytedance.aivideo.engine.ffmpeg.local;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.FfmpegEngineProperties;
import com.bytedance.aivideo.engine.ffmpeg.api.MediaProbeEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.MediaProbeResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 本地 FFprobe 引擎实现（基于 ProcessBuilder）。
 */
@Component
@Slf4j
public class LocalFfprobeEngine implements MediaProbeEngine {

    private final ObjectMapper objectMapper;
    private final FfmpegEngineProperties ffmpegEngineProperties;

    public LocalFfprobeEngine(ObjectMapper objectMapper, FfmpegEngineProperties ffmpegEngineProperties) {
        this.objectMapper = objectMapper;
        this.ffmpegEngineProperties = ffmpegEngineProperties;
    }

    @Override
    public MediaProbeResult probe(Path videoPath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegEngineProperties.getPath());
        command.add("-v");
        command.add("error");
        command.add("-print_format");
        command.add("json");
        command.add("-show_streams");
        command.add("-show_format");
        command.add(videoPath.toAbsolutePath().toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);
        long startNano = System.nanoTime();
        log.info("ffprobe start: videoPath={}, command={}", videoPath.toAbsolutePath(), String.join(" ", command));

        try {
            Process process = processBuilder.start();
            // 并发消费 stdout/stderr，避免缓冲区写满造成子进程阻塞。
            CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream());

            boolean finished = process.waitFor(ffmpegEngineProperties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                // 确保流读取任务尽快结束，避免线程泄漏。
                process.waitFor(2, TimeUnit.SECONDS);
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                log.error("ffprobe timeout: videoPath={}, timeoutSec={}, elapsedMs={}",
                        videoPath.toAbsolutePath(),
                        ffmpegEngineProperties.getTimeoutSeconds(),
                        elapsedMs);
                throw new BizException(ErrorCode.FFMPEG_ERROR,
                        "ffprobe 超时: " + Duration.ofSeconds(ffmpegEngineProperties.getTimeoutSeconds()));
            }

            String stdout = joinFuture(stdoutFuture, "stdout");
            String stderr = joinFuture(stderrFuture, "stderr");
            if (process.exitValue() != 0) {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                log.error("ffprobe failed: videoPath={}, exitCode={}, elapsedMs={}, stderr={}",
                        videoPath.toAbsolutePath(), process.exitValue(), elapsedMs, stderr);
                throw new BizException(ErrorCode.FFMPEG_ERROR, "ffprobe 执行失败: " + stderr);
            }

            MediaProbeResult result = parseMediaInfo(stdout);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            log.info("ffprobe finished: videoPath={}, elapsedMs={}, durationSec={}, width={}, height={}, fps={}, codec={}",
                    videoPath.toAbsolutePath(),
                    elapsedMs,
                    result.getDuration(),
                    result.getWidth(),
                    result.getHeight(),
                    result.getFps(),
                    result.getCodec());
            return result;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            log.error("ffprobe invoke exception: videoPath={}, elapsedMs={}, reason={}",
                    videoPath.toAbsolutePath(), elapsedMs, ex.getMessage(), ex);
            throw new BizException(ErrorCode.FFMPEG_ERROR, "ffprobe 调用失败: " + ex.getMessage());
        }
    }

    private CompletableFuture<String> readStreamAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = inputStream) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private String joinFuture(CompletableFuture<String> future, String streamName) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.FFMPEG_ERROR, "读取 ffprobe " + streamName + " 被中断: " + ex.getMessage());
        } catch (ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new BizException(ErrorCode.FFMPEG_ERROR, "读取 ffprobe " + streamName + " 失败: " + ex.getMessage());
        }
    }

    private MediaProbeResult parseMediaInfo(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode formatNode = root.path("format");
            JsonNode streamsNode = root.path("streams");

            JsonNode videoStream = null;
            JsonNode audioStream = null;
            if (streamsNode.isArray()) {
                Iterator<JsonNode> iterator = streamsNode.iterator();
                while (iterator.hasNext()) {
                    JsonNode stream = iterator.next();
                    String codecType = stream.path("codec_type").asText("");
                    if ("video".equalsIgnoreCase(codecType) && videoStream == null) {
                        videoStream = stream;
                    }
                    if ("audio".equalsIgnoreCase(codecType) && audioStream == null) {
                        audioStream = stream;
                    }
                }
            }

            MediaProbeResult result = new MediaProbeResult();
            result.setDuration(parseDouble(formatNode.path("duration").asText(null)));
            result.setBitrate(parseLong(formatNode.path("bit_rate").asText(null)));
            result.setFormat(firstToken(formatNode.path("format_name").asText(null)));

            if (videoStream != null) {
                result.setWidth(videoStream.path("width").isMissingNode() ? null : videoStream.path("width").asInt());
                result.setHeight(videoStream.path("height").isMissingNode() ? null : videoStream.path("height").asInt());
                result.setCodec(videoStream.path("codec_name").asText(null));

                // 优先使用 avg_frame_rate，fallback 到 r_frame_rate。
                String fpsRaw = videoStream.path("avg_frame_rate").asText("0/0");
                if ("0/0".equals(fpsRaw) || fpsRaw.isBlank()) {
                    fpsRaw = videoStream.path("r_frame_rate").asText("0/0");
                }
                result.setFps(parseFps(fpsRaw));
            }

            result.setHasAudio(audioStream != null);
            result.setAudioCodec(audioStream == null ? null : audioStream.path("codec_name").asText(null));

            if (result.getDuration() == null || result.getWidth() == null || result.getHeight() == null) {
                throw new BizException(ErrorCode.FFMPEG_ERROR, "ffprobe 输出缺失关键字段");
            }
            return result;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.FFMPEG_ERROR, "ffprobe 结果解析失败: " + ex.getMessage());
        }
    }

    private Double parseFps(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.contains("/")) {
            return parseDouble(value);
        }
        String[] parts = value.split("/");
        if (parts.length != 2) {
            return null;
        }
        Double numerator = parseDouble(parts[0]);
        Double denominator = parseDouble(parts[1]);
        if (numerator == null || denominator == null || denominator == 0D) {
            return null;
        }
        return Math.round((numerator / denominator) * 1000D) / 1000D;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] tokens = value.split(",");
        return tokens[0].trim();
    }
}
