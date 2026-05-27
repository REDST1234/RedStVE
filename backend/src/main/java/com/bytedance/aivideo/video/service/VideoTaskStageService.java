package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskStageEntity;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskStageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 任务阶段状态服务：
 * 为并行异步阶段提供独立状态写入，避免 progress_step 被覆盖。
 */
@Service
public class VideoTaskStageService {

    public static final String STAGE_TYPE_ASR = "ASR";
    public static final String STAGE_TYPE_SCENE = "SCENE";
    public static final String STAGE_TYPE_KEYFRAME = "KEYFRAME";
    public static final String STAGE_TYPE_TIMELINE = "TIMELINE";
    public static final String STAGE_TYPE_LLM = "LLM";

    public static final String STAGE_STATUS_PENDING = "PENDING";
    public static final String STAGE_STATUS_RUNNING = "RUNNING";
    public static final String STAGE_STATUS_SUCCESS = "SUCCESS";
    public static final String STAGE_STATUS_FAILED = "FAILED";

    private final VideoAnalysisTaskStageMapper taskStageMapper;

    public VideoTaskStageService(VideoAnalysisTaskStageMapper taskStageMapper) {
        this.taskStageMapper = taskStageMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void initStageIfAbsent(String taskId, String stageType) {
        if (taskId == null || taskId.isBlank() || stageType == null || stageType.isBlank()) {
            return;
        }
        VideoAnalysisTaskStageEntity stage = findByTaskAndType(taskId, stageType);
        if (stage != null) {
            return;
        }
        VideoAnalysisTaskStageEntity entity = new VideoAnalysisTaskStageEntity();
        entity.setTaskId(taskId);
        entity.setStageType(stageType);
        entity.setStageStatus(STAGE_STATUS_PENDING);
        entity.setStageProgress(0);
        taskStageMapper.insert(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRunning(String taskId, String stageType) {
        VideoAnalysisTaskStageEntity stage = getOrCreate(taskId, stageType);
        stage.setStageStatus(STAGE_STATUS_RUNNING);
        stage.setStageProgress(10);
        if (stage.getStartedAt() == null) {
            stage.setStartedAt(LocalDateTime.now());
        }
        stage.setEndedAt(null);
        stage.setErrorMessage(null);
        taskStageMapper.updateById(stage);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(String taskId, String stageType) {
        VideoAnalysisTaskStageEntity stage = getOrCreate(taskId, stageType);
        stage.setStageStatus(STAGE_STATUS_SUCCESS);
        stage.setStageProgress(100);
        if (stage.getStartedAt() == null) {
            stage.setStartedAt(LocalDateTime.now());
        }
        stage.setEndedAt(LocalDateTime.now());
        stage.setErrorMessage(null);
        taskStageMapper.updateById(stage);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String taskId, String stageType, String errorMessage) {
        VideoAnalysisTaskStageEntity stage = getOrCreate(taskId, stageType);
        stage.setStageStatus(STAGE_STATUS_FAILED);
        stage.setStageProgress(100);
        if (stage.getStartedAt() == null) {
            stage.setStartedAt(LocalDateTime.now());
        }
        stage.setEndedAt(LocalDateTime.now());
        stage.setErrorMessage(errorMessage);
        taskStageMapper.updateById(stage);
    }

    public boolean isStageRunningOrSuccess(String taskId, String stageType) {
        VideoAnalysisTaskStageEntity stage = findByTaskAndType(taskId, stageType);
        if (stage == null) {
            return false;
        }
        return STAGE_STATUS_RUNNING.equals(stage.getStageStatus())
                || STAGE_STATUS_SUCCESS.equals(stage.getStageStatus());
    }

    private VideoAnalysisTaskStageEntity getOrCreate(String taskId, String stageType) {
        VideoAnalysisTaskStageEntity stage = findByTaskAndType(taskId, stageType);
        if (stage != null) {
            return stage;
        }
        VideoAnalysisTaskStageEntity entity = new VideoAnalysisTaskStageEntity();
        entity.setTaskId(taskId);
        entity.setStageType(stageType);
        entity.setStageStatus(STAGE_STATUS_PENDING);
        entity.setStageProgress(0);
        taskStageMapper.insert(entity);
        return entity;
    }

    private VideoAnalysisTaskStageEntity findByTaskAndType(String taskId, String stageType) {
        return taskStageMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskStageEntity>()
                        .eq(VideoAnalysisTaskStageEntity::getTaskId, taskId)
                        .eq(VideoAnalysisTaskStageEntity::getStageType, stageType)
                        .isNull(VideoAnalysisTaskStageEntity::getDeletedAt)
        );
    }
}
