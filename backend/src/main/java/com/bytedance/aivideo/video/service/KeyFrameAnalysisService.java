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
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 关键帧抽取服务。
 * 紧接在 SceneAnalysisService 之后执行，基于全量高敏切分结果进行 Hook-Locking 策略抽帧。
 */
@Service
@Slf4j
public class KeyFrameAnalysisService {

    private final KeyFrameExtractEngine keyFrameExtractEngine;
    private final VideoAnalysisTaskMapper videoAnalysisTaskMapper;
    private final KeyFrameMapper keyFrameMapper;
    private final VideoTaskStageService videoTaskStageService;
    private final MediaUploadProperties mediaUploadProperties;
    private final ObjectMapper objectMapper;

    public KeyFrameAnalysisService(
            KeyFrameExtractEngine keyFrameExtractEngine,
            VideoAnalysisTaskMapper videoAnalysisTaskMapper,
            KeyFrameMapper keyFrameMapper,
            VideoTaskStageService videoTaskStageService,
            MediaUploadProperties mediaUploadProperties,
            ObjectMapper objectMapper
    ) {
        this.keyFrameExtractEngine = keyFrameExtractEngine;
        this.videoAnalysisTaskMapper = videoAnalysisTaskMapper;
        this.keyFrameMapper = keyFrameMapper;
        this.videoTaskStageService = videoTaskStageService;
        this.mediaUploadProperties = mediaUploadProperties;
        this.objectMapper = objectMapper;
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
                return;
            }
            SceneDetectResult sceneResult = objectMapper.readValue(sceneJsonPath.toFile(), SceneDetectResult.class);

            // 执行 Hook-Locking 策略选点
            List<KeyFrameInfo> keyFrameInfos = selectKeyFrameInfos(sceneResult.getShots());

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
                
                KeyFrameEntity entity = new KeyFrameEntity();
                entity.setTaskId(taskId);
                entity.setFrameIndex(i + 1);
                entity.setTimePoint(java.math.BigDecimal.valueOf(info.timestamp()));
                entity.setSourceShotIndex(info.shotIndex());
                entity.setExtractionReason(info.reason());
                entity.setFilePath(path.toAbsolutePath().toString());
                
                keyFrameMapper.insert(entity);
                successCount++;
            }

            videoTaskStageService.markSuccess(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME);
            log.info("keyframe extract finished: taskId={}, count={}", taskId, successCount);
        } catch (Exception ex) {
            log.error("keyframe extract failed: taskId={}, reason={}", taskId, ex.getMessage(), ex);
            videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_KEYFRAME, ex.getMessage());
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
     * Hook-Locking 抽帧策略：
     * 1. 强制提取首镜头的 开头(0.1s偏移) 与 居中时间点。
     * 2. 剩余所有镜头按 sceneScore 降序，取补充帧。
     * 动态总数 N = min(15, max(5, floor(totalDuration / 20) + 4))
     */
    private List<KeyFrameInfo> selectKeyFrameInfos(List<SceneShot> shots) {
        List<KeyFrameInfo> infos = new ArrayList<>();
        if (shots == null || shots.isEmpty()) {
            return infos;
        }

        double totalDuration = shots.get(shots.size() - 1).getEndTime();
        int maxFrames = (int) Math.min(15, Math.max(5, Math.floor(totalDuration / 20.0) + 4));

        // 1. Hook 槽位：第一个镜头强制抽两帧
        SceneShot hookShot = shots.get(0);
        // 为了防止 0.0 秒是纯黑帧，给一个 0.1s 的微小偏移。若总时长不到 0.1，则取 0
        double hookStart = hookShot.getDuration() > 0.1 ? hookShot.getStartTime() + 0.1 : hookShot.getStartTime();
        infos.add(new KeyFrameInfo(hookStart, hookShot.getShotIndex(), "HOOK_FIRST"));

        double hookMid = hookShot.getStartTime() + hookShot.getDuration() / 2.0;
        // 避免极短视频导致两个点重合
        if (hookMid > hookStart + 0.1) {
            infos.add(new KeyFrameInfo(hookMid, hookShot.getShotIndex(), "HOOK_MID"));
        }

        // 2. 剩余镜头按 sceneScore 降序，取剩余名额
        if (shots.size() > 1) {
            List<SceneShot> rest = new ArrayList<>(shots.subList(1, shots.size()));
            rest.sort((a, b) -> Double.compare(b.getSceneScore() != null ? b.getSceneScore() : 0.0, 
                                               a.getSceneScore() != null ? a.getSceneScore() : 0.0));
            
            int remainingQuota = Math.max(0, maxFrames - infos.size());
            int limit = Math.min(remainingQuota, rest.size());
            for (int i = 0; i < limit; i++) {
                SceneShot target = rest.get(i);
                infos.add(new KeyFrameInfo(target.getStartTime() + target.getDuration() / 2.0, target.getShotIndex(), "TOP_SCORE"));
            }
        }

        // 去重并排序，保证按时间轴顺序
        // 这里为了保持记录和原顺序一致，我们简单按时间升序重排
        infos.sort((a, b) -> Double.compare(a.timestamp(), b.timestamp()));
        
        // 简单去重（防止相同时间点被多次添加）
        List<KeyFrameInfo> distinctInfos = new ArrayList<>();
        Double lastTime = null;
        for (KeyFrameInfo info : infos) {
            if (lastTime == null || Math.abs(info.timestamp() - lastTime) > 0.01) {
                distinctInfos.add(info);
                lastTime = info.timestamp();
            }
        }
        return distinctInfos;
    }

    private Path getFramesOutputDir(String taskId) {
        Path rootPath = Paths.get(mediaUploadProperties.getDirectory()).toAbsolutePath();
        return rootPath.resolve(taskId).resolve("frames").normalize();
    }

    private record KeyFrameInfo(double timestamp, int shotIndex, String reason) {}
}
