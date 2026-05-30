package com.bytedance.aivideo.engine.ffmpeg.local;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.FfmpegCommandProperties;
import com.bytedance.aivideo.engine.ffmpeg.api.MediaProbeEngine;
import com.bytedance.aivideo.engine.ffmpeg.api.SceneDetectorEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.MediaProbeResult;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneDetectResult;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneShot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地 FFmpeg 镜头切分引擎实现。
 */
@Component
@Slf4j
public class LocalFfmpegSceneDetectorEngine implements SceneDetectorEngine {

    private final FfmpegCommandProperties ffmpegProperties;
    private final MediaProbeEngine mediaProbeEngine;

    // 匹配 scdet 的输出，例如：lavfi.scd.time=1.233 和 lavfi.scd.score=45.2
    private static final Pattern TIME_PATTERN = Pattern.compile("lavfi\\.scd\\.time=([0-9.]+)");
    private static final Pattern SCORE_PATTERN = Pattern.compile("lavfi\\.scd\\.score=([0-9.]+)");

    public LocalFfmpegSceneDetectorEngine(FfmpegCommandProperties ffmpegProperties, MediaProbeEngine mediaProbeEngine) {
        this.ffmpegProperties = ffmpegProperties;
        this.mediaProbeEngine = mediaProbeEngine;
    }

    @Async("videoTaskExecutor")
    @Override
    public CompletableFuture<SceneDetectResult> detectScenesAsync(Path videoPath, double threshold) {
        try {
            return CompletableFuture.completedFuture(doDetectScenes(videoPath, threshold));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private SceneDetectResult doDetectScenes(Path videoPath, double threshold) {
        long startMs = System.currentTimeMillis();
        // 1. 获取视频总时长，用于闭合最后一个镜头
        MediaProbeResult probeResult = mediaProbeEngine.probe(videoPath);
        double totalDuration = probeResult.getDuration();

        // 2. 将 0~1 的 threshold 转换为 scdet 需要的 0~100 阈值
        double scdetThreshold = Math.max(0.0, Math.min(100.0, threshold * 100.0));

        // 3. 构建 FFmpeg 命令
        // 使用 scdet 滤镜检测场景，并通过 metadata=print 输出。
        List<String> command = new ArrayList<>();
        command.add(ffmpegProperties.getPath());
        command.add("-v");
        command.add("info");
        command.add("-i");
        command.add(videoPath.toAbsolutePath().toString());
        command.add("-an"); // 禁用音频
        command.add("-vf");
        command.add(String.format("scdet=threshold=%.2f,metadata=print:file=-", scdetThreshold));
        command.add("-f");
        command.add("null");
        command.add("-");

        log.info("ffmpeg scene detect start: videoPath={}, threshold={}, command={}", 
                videoPath.toAbsolutePath(), threshold, String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // 合并 stderr 到 stdout 进行解析

        List<SceneShot> shots = new ArrayList<>();
        Process process = null;
        try {
            process = processBuilder.start();

            // 4. 同步解析日志
            parseLogOutput(process, shots);

            // 5. 超时控制与收尾
            // 镜头切分耗时较长，这里设置较长超时时间（例如原时长的 0.5 倍，但最少 60 秒）
            int timeoutSec = Math.max(60, (int) (totalDuration * 0.5));
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new BizException(ErrorCode.FFMPEG_ERROR, "FFmpeg 镜头切分超时: " + timeoutSec + "s");
            }
            if (process.exitValue() != 0) {
                throw new BizException(ErrorCode.FFMPEG_ERROR, "FFmpeg 镜头切分执行失败，退出码: " + process.exitValue());
            }

            // 6. 后置处理：补充起始镜头，并闭合时间轴
            shots = finalizeShots(shots, totalDuration);

            log.info("ffmpeg scene detect finished: videoPath={}, totalShots={}, elapsedMs={}", 
                    videoPath.toAbsolutePath(), shots.size(), System.currentTimeMillis() - startMs);

            return SceneDetectResult.builder()
                    .shots(shots)
                    .totalShots(shots.size())
                    .build();

        } catch (IOException e) {
            throw new BizException(ErrorCode.FFMPEG_ERROR, "FFmpeg 命令启动失败: " + e.getMessage());
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.FFMPEG_ERROR, "FFmpeg 镜头切分被中断");
        }
    }

    private void parseLogOutput(Process process, List<SceneShot> shots) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            Double lastTime = null;
            Double lastScore = null;

            while ((line = reader.readLine()) != null) {
                // 实时匹配 scd.time 和 scd.score
                Matcher timeMatcher = TIME_PATTERN.matcher(line);
                if (timeMatcher.find()) {
                    lastTime = Double.parseDouble(timeMatcher.group(1));
                }

                Matcher scoreMatcher = SCORE_PATTERN.matcher(line);
                if (scoreMatcher.find()) {
                    lastScore = Double.parseDouble(scoreMatcher.group(1));
                }

                // 当同时收集到 time 和 score 时，记录一个切分点
                if (lastTime != null && lastScore != null) {
                    SceneShot shot = new SceneShot();
                    // 这里暂存为切分点。真实构建 Shot 列表在 finalizeShots 中进行。
                    // 用 endTime 临时表示该切分点的时间
                    shot.setEndTime(lastTime); 
                    shot.setSceneScore(lastScore / 100.0); // 还原回 0~1 的分值
                    shots.add(shot);
                    
                    lastTime = null;
                    lastScore = null;
                }
            }
        }
    }

    private List<SceneShot> finalizeShots(List<SceneShot> rawCuts, double totalDuration) {
        List<SceneShot> finalShots = new ArrayList<>();
        double currentStartTime = 0.0;
        int index = 0;

        for (SceneShot cut : rawCuts) {
            // 过滤无效切点
            if (cut.getEndTime() <= currentStartTime || cut.getEndTime() > totalDuration) {
                continue;
            }

            SceneShot shot = new SceneShot();
            shot.setShotIndex(index++);
            shot.setStartTime(currentStartTime);
            shot.setEndTime(cut.getEndTime());
            shot.setDuration(cut.getEndTime() - currentStartTime);
            // 这里记录的是导致该镜头结束的 切点分数（或者也可以理解为下一个镜头开始的分数）
            shot.setSceneScore(cut.getSceneScore());
            
            finalShots.add(shot);
            currentStartTime = cut.getEndTime();
        }

        // 处理最后一个镜头（或者如果没有发现任何切分点，则全片为一个镜头）
        if (currentStartTime < totalDuration) {
            SceneShot finalShot = new SceneShot();
            finalShot.setShotIndex(index);
            finalShot.setStartTime(currentStartTime);
            finalShot.setEndTime(totalDuration);
            finalShot.setDuration(totalDuration - currentStartTime);
            finalShot.setSceneScore(0.0); // 结尾无分数
            finalShots.add(finalShot);
        }

        return finalShots;
    }
}
