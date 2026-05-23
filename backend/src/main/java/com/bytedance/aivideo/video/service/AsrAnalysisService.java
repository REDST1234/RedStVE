package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.config.AsrProperties;
import com.bytedance.aivideo.engine.asr.api.AsrEngine;
import com.bytedance.aivideo.engine.asr.model.AsrTranscriptionResult;
import com.bytedance.aivideo.engine.ffmpeg.api.AudioExtractEngine;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskEntity;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * ASR 异步分析编排服务。
 */
@Service
@Slf4j
public class AsrAnalysisService {
    private static final String TASK_STATUS_PROCESSING = "PROCESSING";
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    private static final String TASK_STATUS_FAILED = "FAILED";
    private static final String DEFAULT_AUDIO_FILE_NAME = "audio.mp3";

    private final VideoAnalysisTaskMapper videoAnalysisTaskMapper;
    private final AudioExtractEngine audioExtractEngine;
    private final AsrEngine asrEngine;
    private final VideoAnalysisResultService videoAnalysisResultService;
    private final AsrProperties asrProperties;
    private final VideoTaskStageService videoTaskStageService;

    public AsrAnalysisService(
            VideoAnalysisTaskMapper videoAnalysisTaskMapper,
            AudioExtractEngine audioExtractEngine,
            AsrEngine asrEngine,
            VideoAnalysisResultService videoAnalysisResultService,
            AsrProperties asrProperties,
            VideoTaskStageService videoTaskStageService
    ) {
        this.videoAnalysisTaskMapper = videoAnalysisTaskMapper;
        this.audioExtractEngine = audioExtractEngine;
        this.asrEngine = asrEngine;
        this.videoAnalysisResultService = videoAnalysisResultService;
        this.asrProperties = asrProperties;
        this.videoTaskStageService = videoTaskStageService;
    }

    @Async("videoTaskExecutor")
    public void runAsrAsync(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );
        if (task == null) {
            log.warn("asr skip: task not found, taskId={}", taskId);
            return;
        }
        if (task.getSourceFilePath() == null || task.getSourceFilePath().isBlank()) {
            log.warn("asr skip: source file path empty, taskId={}", taskId);
            videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_ASR, "source file path empty");
            videoAnalysisResultService.markAsrFailed(taskId);
            task.setErrorStep("ASR");
            task.setErrorMessage("source file path empty");
            videoAnalysisTaskMapper.updateById(task);
            return;
        }

        videoTaskStageService.markRunning(taskId, VideoTaskStageService.STAGE_TYPE_ASR);
        task.setStatus(TASK_STATUS_PROCESSING);
        task.setProgress(40);
        task.setCompletedAt(null);
        videoAnalysisTaskMapper.updateById(task);

        try {
            Path outputPath = ensureExtractedAudioPath(task);
            AsrTranscriptionResult transcriptionResult = asrEngine.transcribe(outputPath, taskId);
            videoAnalysisResultService.saveAsrResult(taskId, transcriptionResult);
            videoTaskStageService.markSuccess(taskId, VideoTaskStageService.STAGE_TYPE_ASR);
            task.setStatus(TASK_STATUS_COMPLETED);
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            task.setErrorStep(null);
            task.setErrorMessage(null);
            videoAnalysisTaskMapper.updateById(task);
            log.info("asr analysis finished: taskId={}, segmentCount={}", taskId, transcriptionResult.getSegments().size());
        } catch (Exception ex) {
            log.error("asr analysis failed: taskId={}, reason={}", taskId, ex.getMessage(), ex);
            videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_ASR, ex.getMessage());
            videoAnalysisResultService.markAsrFailed(taskId);
            task.setStatus(TASK_STATUS_FAILED);
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            task.setErrorStep("ASR");
            task.setErrorMessage(ex.getMessage());
            videoAnalysisTaskMapper.updateById(task);
        }
    }

    /**
     * 上传后预提取音轨，供后续 ASR 复用。
     */
    @Async("videoTaskExecutor")
    public void prepareAudioAsync(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );
        if (task == null) {
            log.warn("prepare audio skip: task not found, taskId={}", taskId);
            return;
        }
        if (task.getSourceFilePath() == null || task.getSourceFilePath().isBlank()) {
            log.warn("prepare audio skip: source file path empty, taskId={}", taskId);
            return;
        }
        try {
            Path outputPath = buildAudioOutputPath(taskId);
            if (task.getExtractedAudioPath() != null
                    && !task.getExtractedAudioPath().isBlank()
                    && Files.exists(Paths.get(task.getExtractedAudioPath()).toAbsolutePath())) {
                log.info("prepare audio skip: persisted audio already exists, taskId={}, path={}",
                        taskId, task.getExtractedAudioPath());
                return;
            }
            if (Files.exists(outputPath)) {
                task.setExtractedAudioPath(outputPath.toString());
                videoAnalysisTaskMapper.updateById(task);
                log.info("prepare audio reuse: taskId={}, outputPath={}", taskId, outputPath);
                return;
            }
            extractAndPersistAudio(task);
            log.info("prepare audio finished: taskId={}, outputPath={}", taskId, task.getExtractedAudioPath());
        } catch (Exception ex) {
            log.warn("prepare audio failed: taskId={}, reason={}", taskId, ex.getMessage(), ex);
        }
    }

    private Path ensureExtractedAudioPath(VideoAnalysisTaskEntity task) {
        if (task.getExtractedAudioPath() != null && !task.getExtractedAudioPath().isBlank()) {
            Path persistedPath = Paths.get(task.getExtractedAudioPath()).toAbsolutePath();
            if (Files.exists(persistedPath)) {
                return persistedPath;
            }
        }
        return extractAndPersistAudio(task);
    }

    private Path extractAndPersistAudio(VideoAnalysisTaskEntity task) {
        String taskId = task.getTaskId();
        Path sourceVideoPath = Paths.get(task.getSourceFilePath()).toAbsolutePath();
        Path outputPath = buildAudioOutputPath(taskId);
        Path outputDir = outputPath.getParent();
        audioExtractEngine.extractToMp3(sourceVideoPath, outputDir, outputPath.getFileName().toString());
        task.setExtractedAudioPath(outputPath.toString());
        videoAnalysisTaskMapper.updateById(task);
        return outputPath;
    }

    private Path buildAudioOutputPath(String taskId) {
        Path outputDir = Paths.get(asrProperties.getAudioOutputDir()).toAbsolutePath().resolve(taskId);
        return outputDir.resolve(DEFAULT_AUDIO_FILE_NAME).normalize().toAbsolutePath();
    }

    public String getExtractedAudioPath(String taskId) {
        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );
        if (task == null) {
            return null;
        }
        return task.getExtractedAudioPath();
    }
}
