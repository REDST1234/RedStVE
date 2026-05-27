package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.config.MediaUploadProperties;
import com.bytedance.aivideo.engine.ffmpeg.api.SceneDetectorEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneDetectResult;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskEntity;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * 镜头分析服务：协调 FFmpeg 镜头切分，并更新任务进度与数据库状态。
 */
@Service
@Slf4j
public class SceneAnalysisService {

    private final SceneDetectorEngine sceneDetectorEngine;
    private final VideoAnalysisTaskMapper videoAnalysisTaskMapper;
    private final VideoTaskStageService videoTaskStageService;
    private final MediaUploadProperties mediaUploadProperties;
    private final ObjectMapper objectMapper;
    private final KeyFrameAnalysisService keyFrameAnalysisService;

    // 高敏提取模式下，初始默认使用一个极低的物理阈值，供下游 TimelineMatcher 后置过滤
    private static final double HIGH_SENSITIVITY_THRESHOLD = 0.1;

    public SceneAnalysisService(
            SceneDetectorEngine sceneDetectorEngine,
            VideoAnalysisTaskMapper videoAnalysisTaskMapper,
            VideoTaskStageService videoTaskStageService,
            MediaUploadProperties mediaUploadProperties,
            ObjectMapper objectMapper,
            KeyFrameAnalysisService keyFrameAnalysisService
    ) {
        this.sceneDetectorEngine = sceneDetectorEngine;
        this.videoAnalysisTaskMapper = videoAnalysisTaskMapper;
        this.videoTaskStageService = videoTaskStageService;
        this.mediaUploadProperties = mediaUploadProperties;
        this.objectMapper = objectMapper;
        this.keyFrameAnalysisService = keyFrameAnalysisService;
    }

    @Async("videoTaskExecutor")
    public void runSceneDetectAsync(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        
        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );
        
        if (task == null) {
            log.warn("scene detect skip: task not found, taskId={}", taskId);
            return;
        }

        if (task.getSourceFilePath() == null || task.getSourceFilePath().isBlank()) {
            log.warn("scene detect skip: source file path empty, taskId={}", taskId);
            videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_SCENE, "source file path empty");
            return;
        }

        videoTaskStageService.markRunning(taskId, VideoTaskStageService.STAGE_TYPE_SCENE);

        try {
            Path videoPath = Paths.get(task.getSourceFilePath());
            // 执行高敏物理切分提取
            CompletableFuture<SceneDetectResult> futureResult = sceneDetectorEngine.detectScenesAsync(videoPath, HIGH_SENSITIVITY_THRESHOLD);
            SceneDetectResult result = futureResult.join(); // 这里处于独立线程，join阻塞等待FFmpeg执行结束

            // 序列化落盘
            Path resultPath = getSceneResultFilePath(taskId);
            Files.createDirectories(resultPath.getParent());
            objectMapper.writeValue(resultPath.toFile(), result);

            videoTaskStageService.markSuccess(taskId, VideoTaskStageService.STAGE_TYPE_SCENE);
            log.info("scene detect finished: taskId={}, totalShots={}, savedPath={}", taskId, result.getTotalShots(), resultPath);

            // 切分完成后，立即触发关键帧抽取（已解耦为独立异步服务）
            keyFrameAnalysisService.runExtractAsync(taskId);
        } catch (Exception ex) {
            log.error("scene detect failed: taskId={}, reason={}", taskId, ex.getMessage(), ex);
            videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_SCENE, ex.getMessage());
        }
    }

    private Path getSceneResultFilePath(String taskId) {
        Path rootPath = Paths.get(mediaUploadProperties.getDirectory()).toAbsolutePath();
        return rootPath.resolve(taskId).resolve("scene_result.json").normalize();
    }
}
