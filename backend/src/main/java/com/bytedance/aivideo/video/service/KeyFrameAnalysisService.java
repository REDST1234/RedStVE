package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bytedance.aivideo.config.MediaUploadProperties;
import com.bytedance.aivideo.engine.ffmpeg.api.KeyFrameExtractEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneDetectResult;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneShot;
import com.bytedance.aivideo.video.entity.KeyFrameEntity;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskEntity;
import com.bytedance.aivideo.video.mapper.KeyFrameMapper;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 关键帧抽取服务。
 * 紧接在 SceneAnalysisService 之后执行，基于全量高敏切分结果进行 Hook-Locking 策略抽帧。
 */
@Service
@Slf4j
public class KeyFrameAnalysisService {

    private static final double HOOK_START_OFFSET_SEC = 0.10D;
    private static final double BOUNDARY_DELTA_SEC = 0.15D;
    private static final double UNIFORM_SAMPLE_INTERVAL_SEC = 2.0D;
    private static final double LONG_SHOT_MIDPOINT_THRESHOLD_SEC = 2.5D;
    private static final double CUT_SCORE_THRESHOLD_FOR_BOUNDARY_BOOST = 0.15D;
    private static final double MIN_FRAME_GAP_SEC = 0.08D;
    private static final double UNIFORM_SAMPLE_SKIP_RADIUS_SEC = 0.60D;
    private static final int MAX_FRAMES = 24;
    private static final Set<String> ALLOWED_EXTRACTION_REASONS = Set.of(
            "HOOK_FIRST",
            "HOOK_MID",
            "TOP_SCORE",
            "BOUNDARY_PRE",
            "BOUNDARY_POST",
            "UNIFORM_SAMPLE",
            "LONG_SHOT_MID"
    );

    private final KeyFrameExtractEngine keyFrameExtractEngine;
    private final VideoAnalysisTaskMapper videoAnalysisTaskMapper;
    private final KeyFrameMapper keyFrameMapper;
    private final VideoTaskStageService videoTaskStageService;
    private final MediaUploadProperties mediaUploadProperties;
    private final ObjectMapper objectMapper;
    private final VideoAnalysisResultService videoAnalysisResultService;

    public KeyFrameAnalysisService(
            KeyFrameExtractEngine keyFrameExtractEngine,
            VideoAnalysisTaskMapper videoAnalysisTaskMapper,
            KeyFrameMapper keyFrameMapper,
            VideoTaskStageService videoTaskStageService,
            MediaUploadProperties mediaUploadProperties,
            ObjectMapper objectMapper,
            VideoAnalysisResultService videoAnalysisResultService
    ) {
        this.keyFrameExtractEngine = keyFrameExtractEngine;
        this.videoAnalysisTaskMapper = videoAnalysisTaskMapper;
        this.keyFrameMapper = keyFrameMapper;
        this.videoTaskStageService = videoTaskStageService;
        this.mediaUploadProperties = mediaUploadProperties;
        this.objectMapper = objectMapper;
        this.videoAnalysisResultService = videoAnalysisResultService;
    }

    @Async("videoTaskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void runExtractAsync(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }

        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );

        if (task == null || task.getSourceFilePath() == null || task.getSourceFilePath().isBlank()) {
            log.warn("keyframe extract skip: task or source path empty, taskId={}", taskId);
            return;
        }

        videoTaskStageService.initStageIfAbsent(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME);
        videoTaskStageService.markRunning(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME);

        try {
            Path videoPath = Paths.get(task.getSourceFilePath());
            Path outputDir = getFramesOutputDir(taskId);

            // 从磁盘读取 scene_result.json
            Path sceneJsonPath = outputDir.getParent().resolve("scene_result.json");
            if (!Files.exists(sceneJsonPath)) {
                log.warn("keyframe extract skip: scene_result.json not found, taskId={}", taskId);
                videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME, "scene_result.json not found");
                videoAnalysisResultService.markTaskFailed(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME, "scene_result.json not found");
                return;
            }
            SceneDetectResult sceneResult = objectMapper.readValue(sceneJsonPath.toFile(), SceneDetectResult.class);

            // 执行 Hook-Locking 策略选点
            List<KeyFrameInfo> keyFrameInfos = selectKeyFrameInfos(sceneResult.getShots());
            log.info("keyframe extract selected reasons: taskId={}, reasons={}",
                    taskId,
                    keyFrameInfos.stream().map(KeyFrameInfo::reason).distinct().toList());

            if (keyFrameInfos.isEmpty()) {
                videoTaskStageService.markSuccess(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME);
                return;
            }

            List<Double> timestamps = keyFrameInfos.stream().map(KeyFrameInfo::timestamp).toList();

            // 执行引擎抽取
            CompletableFuture<List<Path>> futureResult = keyFrameExtractEngine.extractFramesAsync(videoPath, timestamps, outputDir);
            List<Path> extractedPaths = futureResult.join(); // 阻塞等待异步执行完毕

            // 同一 task 再次抽帧时，先逻辑删除旧记录，再写入新记录。
            softDeleteExistingKeyFrames(taskId);

            // 写入关键帧表
            int successCount = 0;
            for (int i = 0; i < extractedPaths.size() && i < keyFrameInfos.size(); i++) {
                Path path = extractedPaths.get(i);
                KeyFrameInfo info = keyFrameInfos.get(i);
                String normalizedReason = normalizeExtractionReason(info.reason());
                if (!ALLOWED_EXTRACTION_REASONS.contains(normalizedReason)) {
                    log.error("keyframe extract invalid extractionReason before insert: taskId={}, frameIndex={}, rawReason={}, normalizedReason={}",
                            taskId, i + 1, info.reason(), normalizedReason);
                    throw new IllegalStateException("Unsupported extractionReason before insert: " + normalizedReason);
                }
                
                KeyFrameEntity entity = new KeyFrameEntity();
                entity.setTaskId(taskId);
                entity.setFrameIndex(i + 1);
                entity.setTimePoint(java.math.BigDecimal.valueOf(info.timestamp()));
                entity.setSourceShotIndex(info.shotIndex());
                entity.setExtractionReason(normalizedReason);
                entity.setFilePath(path.toAbsolutePath().toString());
                
                keyFrameMapper.insert(entity);
                successCount++;
            }

            videoTaskStageService.markSuccess(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME);
            log.info("keyframe extract finished: taskId={}, count={}", taskId, successCount);
        } catch (Exception ex) {
            log.error("keyframe extract failed: taskId={}, reason={}", taskId, ex.getMessage(), ex);
            videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME, ex.getMessage());
            videoAnalysisResultService.markTaskFailed(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME, ex.getMessage());
        }
    }

    private void softDeleteExistingKeyFrames(String taskId) {
        LocalDateTime deletedAt = LocalDateTime.now();
        keyFrameMapper.update(
                null,
                new LambdaUpdateWrapper<KeyFrameEntity>()
                        .eq(KeyFrameEntity::getTaskId, taskId)
                        .isNull(KeyFrameEntity::getDeletedAt)
                        .set(KeyFrameEntity::getDeletedAt, deletedAt)
        );
    }

    /**
     * P0 关键帧策略：
     * 1. 强制提取首镜头的 HOOK_FIRST / HOOK_MID。
     * 2. 对高价值 cut 点补帧：BOUNDARY_PRE / BOUNDARY_POST。
     * 3. 对长镜头补 LONG_SHOT_MID。
     * 4. 对全片追加低密度均匀采样 UNIFORM_SAMPLE 作为兜底覆盖。
     */
    private List<KeyFrameInfo> selectKeyFrameInfos(List<SceneShot> shots) {
        List<KeyFrameInfo> infos = new ArrayList<>();
        if (shots == null || shots.isEmpty()) {
            return infos;
        }

        double totalDuration = resolveTotalDuration(shots);
        if (totalDuration <= 0) {
            return infos;
        }

        addHookFrames(shots.get(0), infos);
        addBoundaryBoostFrames(shots, infos);
        addLongShotMidFrames(shots, infos);
        addUniformSampleFrames(shots, totalDuration, infos);

        return normalizeAndTrim(infos, MAX_FRAMES);
    }

    private void addHookFrames(SceneShot hookShot, List<KeyFrameInfo> infos) {
        if (hookShot == null) {
            return;
        }
        double hookStart = clampWithinShot(
                safeStart(hookShot) + HOOK_START_OFFSET_SEC,
                hookShot
        );
        infos.add(new KeyFrameInfo(hookStart, safeShotIndex(hookShot), "HOOK_FIRST"));

        double hookMid = midpoint(hookShot);
        if (Math.abs(hookMid - hookStart) > MIN_FRAME_GAP_SEC) {
            infos.add(new KeyFrameInfo(hookMid, safeShotIndex(hookShot), "HOOK_MID"));
        }
    }

    private void addBoundaryBoostFrames(List<SceneShot> shots, List<KeyFrameInfo> infos) {
        for (int i = 1; i < shots.size(); i++) {
            SceneShot current = shots.get(i);
            if (!isHighValueCut(current)) {
                continue;
            }

            SceneShot previous = shots.get(i - 1);
            double cutTime = resolveCutTime(previous, current);
            infos.add(new KeyFrameInfo(
                    clampWithinShot(cutTime - BOUNDARY_DELTA_SEC, previous),
                    safeShotIndex(previous),
                    "BOUNDARY_PRE"
            ));
            infos.add(new KeyFrameInfo(
                    clampWithinShot(cutTime + BOUNDARY_DELTA_SEC, current),
                    safeShotIndex(current),
                    "BOUNDARY_POST"
            ));
        }
    }

    private void addLongShotMidFrames(List<SceneShot> shots, List<KeyFrameInfo> infos) {
        for (SceneShot shot : shots) {
            if (safeDuration(shot) < LONG_SHOT_MIDPOINT_THRESHOLD_SEC) {
                continue;
            }
            int shotIndex = safeShotIndex(shot);
            if (countFramesForShot(infos, shotIndex) >= 2) {
                continue;
            }
            infos.add(new KeyFrameInfo(midpoint(shot), shotIndex, "LONG_SHOT_MID"));
        }
    }

    private void addUniformSampleFrames(List<SceneShot> shots, double totalDuration, List<KeyFrameInfo> infos) {
        for (double timestamp = UNIFORM_SAMPLE_INTERVAL_SEC; timestamp < totalDuration; timestamp += UNIFORM_SAMPLE_INTERVAL_SEC) {
            if (hasNearbyFrame(infos, timestamp, UNIFORM_SAMPLE_SKIP_RADIUS_SEC)) {
                continue;
            }
            SceneShot shot = findShotByTime(shots, timestamp);
            if (shot == null) {
                continue;
            }
            infos.add(new KeyFrameInfo(
                    clampWithinShot(timestamp, shot),
                    safeShotIndex(shot),
                    "UNIFORM_SAMPLE"
            ));
        }
    }

    private List<KeyFrameInfo> normalizeAndTrim(List<KeyFrameInfo> infos, int maxFrames) {
        List<KeyFrameInfo> prioritized = new ArrayList<>(infos);
        prioritized.sort(Comparator
                .comparingInt((KeyFrameInfo info) -> reasonPriority(info.reason()))
                .thenComparingDouble(KeyFrameInfo::timestamp));

        List<KeyFrameInfo> distinctInfos = new ArrayList<>();
        for (KeyFrameInfo info : prioritized) {
            if (hasNearbyFrame(distinctInfos, info.timestamp(), MIN_FRAME_GAP_SEC)) {
                continue;
            }
            distinctInfos.add(info);
            if (distinctInfos.size() >= maxFrames) {
                break;
            }
        }

        distinctInfos.sort(Comparator.comparingDouble(KeyFrameInfo::timestamp));
        return distinctInfos;
    }

    private double resolveTotalDuration(List<SceneShot> shots) {
        SceneShot last = shots.get(shots.size() - 1);
        double end = safeEnd(last);
        if (end > 0) {
            return end;
        }
        return shots.stream().mapToDouble(this::safeDuration).sum();
    }

    private boolean isHighValueCut(SceneShot shot) {
        double score = shot != null && shot.getSceneScore() != null ? shot.getSceneScore() : 0.0D;
        return score >= CUT_SCORE_THRESHOLD_FOR_BOUNDARY_BOOST;
    }

    private double resolveCutTime(SceneShot previous, SceneShot current) {
        double currentStart = safeStart(current);
        if (currentStart > 0) {
            return currentStart;
        }
        return safeEnd(previous);
    }

    private SceneShot findShotByTime(List<SceneShot> shots, double timestamp) {
        for (SceneShot shot : shots) {
            double start = safeStart(shot);
            double end = safeEnd(shot);
            if (timestamp >= start && timestamp <= end) {
                return shot;
            }
        }
        return shots.isEmpty() ? null : shots.get(shots.size() - 1);
    }

    private int countFramesForShot(List<KeyFrameInfo> infos, int shotIndex) {
        int count = 0;
        for (KeyFrameInfo info : infos) {
            if (info.shotIndex() == shotIndex) {
                count++;
            }
        }
        return count;
    }

    private boolean hasNearbyFrame(List<KeyFrameInfo> infos, double timestamp, double radiusSec) {
        for (KeyFrameInfo info : infos) {
            if (Math.abs(info.timestamp() - timestamp) <= radiusSec) {
                return true;
            }
        }
        return false;
    }

    private double clampWithinShot(double candidate, SceneShot shot) {
        double start = safeStart(shot);
        double end = safeEnd(shot);
        if (end <= start) {
            return start;
        }
        double min = Math.min(start + 0.02D, end);
        double max = Math.max(min, end - 0.02D);
        if (candidate < min) {
            return min;
        }
        if (candidate > max) {
            return max;
        }
        return candidate;
    }

    private double midpoint(SceneShot shot) {
        return safeStart(shot) + safeDuration(shot) / 2.0D;
    }

    private double safeStart(SceneShot shot) {
        return shot != null && shot.getStartTime() != null ? shot.getStartTime() : 0.0D;
    }

    private double safeEnd(SceneShot shot) {
        return shot != null && shot.getEndTime() != null ? shot.getEndTime() : safeStart(shot) + safeDuration(shot);
    }

    private double safeDuration(SceneShot shot) {
        if (shot == null) {
            return 0.0D;
        }
        if (shot.getDuration() != null) {
            return Math.max(0.0D, shot.getDuration());
        }
        return Math.max(0.0D, safeEnd(shot) - safeStart(shot));
    }

    private int safeShotIndex(SceneShot shot) {
        return shot != null && shot.getShotIndex() != null ? shot.getShotIndex() : -1;
    }

    private int reasonPriority(String reason) {
        if (reason == null) {
            return 99;
        }
        return switch (reason) {
            case "HOOK_FIRST" -> 0;
            case "HOOK_MID" -> 1;
            case "BOUNDARY_PRE", "BOUNDARY_POST" -> 2;
            case "LONG_SHOT_MID" -> 3;
            case "UNIFORM_SAMPLE" -> 4;
            case "TOP_SCORE" -> 5;
            default -> 10;
        };
    }

    private String normalizeExtractionReason(String reason) {
        return reason == null ? null : reason.trim();
    }

    private Path getFramesOutputDir(String taskId) {
        Path rootPath = Paths.get(mediaUploadProperties.getDirectory()).toAbsolutePath();
        return rootPath.resolve(taskId).resolve("frames").normalize();
    }

    private record KeyFrameInfo(double timestamp, int shotIndex, String reason) {}
}
