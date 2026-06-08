package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskEntity;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskStageEntity;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 拆解链路失败后按阶段重试，避免重复执行已成功阶段。
 */
@Service
public class VideoAnalysisRetryService {

    private static final List<String> STAGE_ORDER = List.of(
            VideoTaskStageService.STAGE_TYPE_ASR,
            VideoTaskStageService.STAGE_TYPE_SCENE,
            VideoTaskStageService.STAGE_TYPE_KEYFRAME,
            VideoTaskStageService.STAGE_TYPE_TIMELINE,
            VideoTaskStageService.STAGE_TYPE_LLM
    );

    private final VideoAnalysisTaskMapper videoAnalysisTaskMapper;
    private final VideoTaskStageService videoTaskStageService;
    private final VideoAnalysisResultService videoAnalysisResultService;
    private final AsrAnalysisService asrAnalysisService;
    private final SceneAnalysisService sceneAnalysisService;
    private final KeyFrameAnalysisService keyFrameAnalysisService;
    private final StructureAnalyzerService structureAnalyzerService;

    public VideoAnalysisRetryService(VideoAnalysisTaskMapper videoAnalysisTaskMapper,
                                     VideoTaskStageService videoTaskStageService,
                                     VideoAnalysisResultService videoAnalysisResultService,
                                     AsrAnalysisService asrAnalysisService,
                                     SceneAnalysisService sceneAnalysisService,
                                     KeyFrameAnalysisService keyFrameAnalysisService,
                                     StructureAnalyzerService structureAnalyzerService) {
        this.videoAnalysisTaskMapper = videoAnalysisTaskMapper;
        this.videoTaskStageService = videoTaskStageService;
        this.videoAnalysisResultService = videoAnalysisResultService;
        this.asrAnalysisService = asrAnalysisService;
        this.sceneAnalysisService = sceneAnalysisService;
        this.keyFrameAnalysisService = keyFrameAnalysisService;
        this.structureAnalyzerService = structureAnalyzerService;
    }

    @Transactional(rollbackFor = Exception.class)
    public String retryFromFailedStage(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }

        String failedStage = resolveFirstFailedStage(taskId);
        if (failedStage == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "当前任务没有可重试的失败阶段");
        }

        resetFailedStageAndDownstream(taskId, failedStage);
        videoAnalysisResultService.markTaskProcessingForRetry(taskId, failedStage);
        dispatchRetry(taskId, failedStage);
        return failedStage;
    }

    @Transactional(rollbackFor = Exception.class)
    public String retryLlmOnly(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }

        String targetStage = VideoTaskStageService.STAGE_TYPE_LLM;
        resetFailedStageAndDownstream(taskId, targetStage);
        videoAnalysisResultService.markTaskProcessingForRetry(taskId, targetStage);
        dispatchRetry(taskId, targetStage);
        return targetStage;
    }

    private String resolveFirstFailedStage(String taskId) {
        for (String stageType : STAGE_ORDER) {
            VideoAnalysisTaskStageEntity stage = videoTaskStageService.getStage(taskId, stageType);
            if (stage != null && VideoTaskStageService.STAGE_STATUS_FAILED.equals(stage.getStageStatus())) {
                return stageType;
            }
        }
        return null;
    }

    private void resetFailedStageAndDownstream(String taskId, String failedStage) {
        boolean reset = false;
        for (String stageType : STAGE_ORDER) {
            if (stageType.equals(failedStage)) {
                reset = true;
            }
            if (reset) {
                videoTaskStageService.resetToPending(taskId, stageType);
            }
        }
    }

    private void dispatchRetry(String taskId, String failedStage) {
        switch (failedStage) {
            case VideoTaskStageService.STAGE_TYPE_ASR -> asrAnalysisService.runAsrAsync(taskId);
            case VideoTaskStageService.STAGE_TYPE_SCENE -> sceneAnalysisService.runSceneDetectAsync(taskId);
            case VideoTaskStageService.STAGE_TYPE_KEYFRAME -> keyFrameAnalysisService.runExtractAsync(taskId);
            case VideoTaskStageService.STAGE_TYPE_TIMELINE -> structureAnalyzerService.runTimelineAndLlmAsync(taskId);
            case VideoTaskStageService.STAGE_TYPE_LLM -> structureAnalyzerService.runLlmOnlyAsync(taskId);
            default -> throw new BizException(ErrorCode.INVALID_REQUEST, "不支持的重试阶段: " + failedStage);
        }
    }
}
