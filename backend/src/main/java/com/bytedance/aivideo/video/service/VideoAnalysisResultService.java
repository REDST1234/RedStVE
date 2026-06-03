package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.engine.asr.model.AsrSegmentResult;
import com.bytedance.aivideo.engine.asr.model.AsrTranscriptionResult;
import com.bytedance.aivideo.video.dto.AnalysisResultWriteCommand;
import com.bytedance.aivideo.video.dto.MediaInfo;
import com.bytedance.aivideo.video.dto.VideoTaskResultResponse;
import com.bytedance.aivideo.video.dto.timeline.TimelineMatchResult;
import com.bytedance.aivideo.video.entity.AnalysisResultCoreEntity;
import com.bytedance.aivideo.video.entity.AnalysisResultTextAssetEntity;
import com.bytedance.aivideo.video.entity.AnalysisResultTimelineAssetEntity;
import com.bytedance.aivideo.video.entity.AsrSegmentEntity;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskEntity;
import com.bytedance.aivideo.video.mapper.AnalysisResultCoreMapper;
import com.bytedance.aivideo.video.mapper.AnalysisResultTextAssetMapper;
import com.bytedance.aivideo.video.mapper.AnalysisResultTimelineAssetMapper;
import com.bytedance.aivideo.video.mapper.AsrSegmentMapper;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskMapper;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskStageMapper;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskStageEntity;
import com.bytedance.aivideo.video.dto.TaskStageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.bytedance.aivideo.config.MediaUploadProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 拆解结果三层表读写服务。
 */
@Slf4j
@Service
public class VideoAnalysisResultService {

    private final AnalysisResultCoreMapper coreMapper;
    private final AnalysisResultTextAssetMapper textAssetMapper;
    private final AnalysisResultTimelineAssetMapper timelineAssetMapper;
    private final AsrSegmentMapper asrSegmentMapper;
    private final VideoAnalysisTaskMapper videoAnalysisTaskMapper;
    private final VideoAnalysisTaskStageMapper taskStageMapper;
    private final ObjectMapper objectMapper;
    private final MediaUploadProperties mediaUploadProperties;
    private static final String TASK_STATUS_PROCESSING = "PROCESSING";
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    private static final String TASK_STATUS_FAILED = "FAILED";

    public VideoAnalysisResultService(
            AnalysisResultCoreMapper coreMapper,
            AnalysisResultTextAssetMapper textAssetMapper,
            AnalysisResultTimelineAssetMapper timelineAssetMapper,
            AsrSegmentMapper asrSegmentMapper,
            VideoAnalysisTaskMapper videoAnalysisTaskMapper,
            VideoAnalysisTaskStageMapper taskStageMapper,
            ObjectMapper objectMapper,
            MediaUploadProperties mediaUploadProperties
    ) {
        this.coreMapper = coreMapper;
        this.textAssetMapper = textAssetMapper;
        this.timelineAssetMapper = timelineAssetMapper;
        this.asrSegmentMapper = asrSegmentMapper;
        this.videoAnalysisTaskMapper = videoAnalysisTaskMapper;
        this.taskStageMapper = taskStageMapper;
        this.objectMapper = objectMapper;
        this.mediaUploadProperties = mediaUploadProperties;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveResult(AnalysisResultWriteCommand command) {
        if (command == null || command.getTaskId() == null || command.getTaskId().isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        if (command.getBizId() == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bizId 不能为空");
        }

        upsertCore(command);
        upsertTextAsset(command);
        upsertTimelineAsset(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveTimelineDebug(String taskId, String fatTimelineJson) {
        AnalysisResultCoreEntity core = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>()
                        .eq(AnalysisResultCoreEntity::getTaskId, taskId)
                        .isNull(AnalysisResultCoreEntity::getDeletedAt)
        );
        if (core == null || core.getBizId() == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "任务结果不存在: " + taskId);
        }
        
        AnalysisResultWriteCommand command = new AnalysisResultWriteCommand();
        command.setTaskId(taskId);
        command.setBizId(core.getBizId());
        command.setFatTimelineJson(fatTimelineJson);
        
        upsertTimelineAsset(command);
    }

    /**
     * 仅保存 LLM 分析链路产生的 timeline 相关结果，避免覆盖 core/text 现有数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveLlmTimelineResult(
            String taskId,
            String fatTimelineJson,
            String refinedTimelineJson,
            String videoStructureTemplateJson
    ) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        AnalysisResultCoreEntity core = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>()
                        .eq(AnalysisResultCoreEntity::getTaskId, taskId)
                        .isNull(AnalysisResultCoreEntity::getDeletedAt)
        );
        if (core == null || core.getBizId() == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "任务结果不存在: " + taskId);
        }

        AnalysisResultWriteCommand command = new AnalysisResultWriteCommand();
        command.setTaskId(taskId.trim());
        command.setBizId(core.getBizId());
        command.setFatTimelineJson(fatTimelineJson);
        command.setRefinedTimelineJson(refinedTimelineJson);
        command.setVideoStructureTemplateJson(videoStructureTemplateJson);
        upsertTimelineAsset(command);
    }

    public VideoTaskResultResponse getTaskResult(String taskId, boolean includeTimeline) {
        AnalysisResultCoreEntity core = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>()
                        .eq(AnalysisResultCoreEntity::getTaskId, taskId)
                        .isNull(AnalysisResultCoreEntity::getDeletedAt)
        );
        if (core == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "任务结果不存在: " + taskId);
        }

        AnalysisResultTextAssetEntity textAsset = textAssetMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultTextAssetEntity>()
                        .eq(AnalysisResultTextAssetEntity::getTaskId, taskId)
                        .isNull(AnalysisResultTextAssetEntity::getDeletedAt)
        );

        AnalysisResultTimelineAssetEntity timelineAsset = null;
        if (includeTimeline) {
            timelineAsset = timelineAssetMapper.selectOne(
                    new LambdaQueryWrapper<AnalysisResultTimelineAssetEntity>()
                            .eq(AnalysisResultTimelineAssetEntity::getTaskId, taskId)
                            .isNull(AnalysisResultTimelineAssetEntity::getDeletedAt)
            );
        }

        List<AsrSegmentEntity> asrSegments = asrSegmentMapper.selectList(
                new LambdaQueryWrapper<AsrSegmentEntity>()
                        .eq(AsrSegmentEntity::getTaskId, taskId)
                        .isNull(AsrSegmentEntity::getDeletedAt)
                        .orderByAsc(AsrSegmentEntity::getSegmentIndex)
        );

        VideoTaskResultResponse response = new VideoTaskResultResponse();
        response.setTaskId(taskId);
        response.setStatus(core.getStatus());
        response.setVideoInfo(buildVideoInfo(core));
        response.setCategoryId(core.getCategoryId());
        response.setPartialFailedDimensions(parseJsonArray(core.getPartialFailedDimensions()));
        response.setTranscript(buildTranscript(asrSegments));

        List<VideoAnalysisTaskStageEntity> stageEntities = taskStageMapper.selectList(
                new LambdaQueryWrapper<VideoAnalysisTaskStageEntity>()
                        .eq(VideoAnalysisTaskStageEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskStageEntity::getDeletedAt)
        );
        List<TaskStageDto> stageDtos = new ArrayList<>();
        if (stageEntities != null) {
            for (VideoAnalysisTaskStageEntity stage : stageEntities) {
                TaskStageDto dto = new TaskStageDto();
                dto.setStageType(stage.getStageType());
                dto.setStageStatus(stage.getStageStatus());
                dto.setStageProgress(stage.getStageProgress());
                dto.setStartedAt(stage.getStartedAt());
                dto.setEndedAt(stage.getEndedAt());
                dto.setErrorMessage(stage.getErrorMessage());
                stageDtos.add(dto);
            }
        }
        response.setStages(stageDtos);

        if (includeTimeline && timelineAsset != null) {
            response.setFatTimeline(parseJsonObject(timelineAsset.getFatTimelineJson()));
            response.setRefinedTimeline(parseJsonObject(timelineAsset.getRefinedTimelineJson()));
            response.setVideoStructureTemplate(parseJsonObject(timelineAsset.getVideoStructureTemplateJson()));
        } else {
            response.setFatTimeline(null);
            response.setRefinedTimeline(null);
            response.setVideoStructureTemplate(null);
        }

        if (textAsset != null && (response.getTranscript() == null || response.getTranscript().isEmpty())
                && textAsset.getAsrFullText() != null && !textAsset.getAsrFullText().isBlank()) {
            VideoTaskResultResponse.TranscriptItem item = new VideoTaskResultResponse.TranscriptItem();
            item.setText(textAsset.getAsrFullText());
            response.setTranscript(List.of(item));
        }

        return response;
    }

    /**
     * 获取原始的 scene_result.json 内容。
     */
    public Object getRawSceneResult(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        Path rootPath = Paths.get(mediaUploadProperties.getDirectory()).toAbsolutePath();
        Path resultPath = rootPath.resolve(taskId).resolve("scene_result.json").normalize();
        if (!Files.exists(resultPath)) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "scene_result.json 文件不存在: " + taskId);
        }
        try {
            String json = Files.readString(resultPath);
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "读取 scene_result.json 失败: " + ex.getMessage());
        }
    }

    /**
     * 仅写入 FFprobe 基础结果到核心表，供上传阶段快速落库。
     */
    @Transactional(rollbackFor = Exception.class)
    public void upsertProbeCore(Long materialBizId, String taskId, MediaInfo mediaInfo) {
        if (materialBizId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "materialBizId 不能为空");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }

        AnalysisResultCoreEntity entity = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>().eq(AnalysisResultCoreEntity::getTaskId, taskId)
        );
        if (entity == null) {
            entity = new AnalysisResultCoreEntity();
            entity.setTaskId(taskId);
            // 与上传素材使用同一业务主键，便于跨表追踪
            entity.setBizId(materialBizId);
            // probe 阶段只代表媒体信息就绪，不代表全链路完成。
            entity.setStatus(TASK_STATUS_PROCESSING);
            entity.setSchemaVersion("v2");
            entity.setShotCount(0);
            entity.setAsrSegmentCount(0);
            entity.setKeyFrameCount(0);
        }

        fillProbeFields(entity, mediaInfo);
        if (entity.getId() == null) {
            coreMapper.insert(entity);
            return;
        }
        coreMapper.updateById(entity);
    }

    /**
     * 保存 ASR 结果（片段表 + 文本资产层 + 核心计数）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAsrResult(String taskId, AsrTranscriptionResult transcriptionResult) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        if (transcriptionResult == null || transcriptionResult.getSegments() == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "ASR 结果不能为空");
        }

        AnalysisResultCoreEntity core = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>().eq(AnalysisResultCoreEntity::getTaskId, taskId)
        );
        if (core == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "任务结果不存在: " + taskId);
        }

        LocalDateTime deletedAt = LocalDateTime.now();
        asrSegmentMapper.update(
                null,
                new LambdaUpdateWrapper<AsrSegmentEntity>()
                        .eq(AsrSegmentEntity::getTaskId, taskId)
                        .isNull(AsrSegmentEntity::getDeletedAt)
                        .set(AsrSegmentEntity::getDeletedAt, deletedAt)
        );
        int fallbackIndex = 0;
        Set<Integer> usedSegmentIndexes = new HashSet<>();
        for (AsrSegmentResult segment : transcriptionResult.getSegments()) {
            AsrSegmentEntity entity = new AsrSegmentEntity();
            entity.setTaskId(taskId);
            entity.setSegmentIndex(resolveSegmentIndex(segment.getSegmentIndex(), fallbackIndex, usedSegmentIndexes));
            // DB 约束 text 非空，语义片段（如纯 FX/SILENT）无语音文本时落空串而非 null。
            entity.setText(segment.getText() == null ? "" : segment.getText());
            entity.setStartTime(BigDecimal.valueOf(segment.getStartSec()));
            entity.setEndTime(BigDecimal.valueOf(segment.getEndSec()));
            entity.setSpeakerLabel(segment.getSpeakerLabel());
            entity.setConfidence(segment.getConfidence() == null ? null : BigDecimal.valueOf(segment.getConfidence()));
            entity.setAudioEmotion(segment.getAudioEmotion());
            entity.setVolumeIntensity(segment.getVolumeIntensity());
            entity.setBackgroundEnvironment(segment.getBackgroundEnvironment());
            entity.setVocalVibe(segment.getVocalVibe());
            entity.setBgmGenre(segment.getBgmGenre());
            entity.setBgmInstruments(segment.getBgmInstruments());
            entity.setDeletedAt(null);
            asrSegmentMapper.insert(entity);
            fallbackIndex++;
        }

        core.setAsrSegmentCount(transcriptionResult.getSegments().size());
        core.setStatus(TASK_STATUS_COMPLETED);
        removeFailedDimension(core, "asr");
        coreMapper.updateById(core);

        AnalysisResultTextAssetEntity textAsset = textAssetMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultTextAssetEntity>().eq(AnalysisResultTextAssetEntity::getTaskId, taskId)
        );
        if (textAsset == null) {
            textAsset = new AnalysisResultTextAssetEntity();
            textAsset.setBizId(core.getBizId());
            textAsset.setTaskId(taskId);
            textAsset.setAsrFullText(transcriptionResult.getFullText());
            textAssetMapper.insert(textAsset);
            return;
        }
        textAsset.setAsrFullText(transcriptionResult.getFullText());
        textAssetMapper.updateById(textAsset);
    }

    /**
     * 标记 ASR 维度失败，不影响其它维度读取。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsrFailed(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        AnalysisResultCoreEntity core = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>().eq(AnalysisResultCoreEntity::getTaskId, taskId)
        );
        if (core == null) {
            return;
        }
        core.setStatus(TASK_STATUS_FAILED);
        addFailedDimension(core, "asr");
        coreMapper.updateById(core);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markTaskFailed(String taskId, String stageType, String errorMessage) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        AnalysisResultCoreEntity core = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>().eq(AnalysisResultCoreEntity::getTaskId, taskId)
        );
        if (core != null) {
            core.setStatus(TASK_STATUS_FAILED);
            if (stageType != null && !stageType.isBlank()) {
                addFailedDimension(core, normalizeFailedDimension(stageType));
            }
            coreMapper.updateById(core);
        }

        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );
        if (task == null) {
            return;
        }
        task.setStatus(TASK_STATUS_FAILED);
        task.setProgress(100);
        task.setCompletedAt(LocalDateTime.now());
        task.setErrorStep(stageType);
        task.setErrorMessage(errorMessage);
        videoAnalysisTaskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markTaskProcessingForRetry(String taskId, String stageType) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        AnalysisResultCoreEntity core = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>().eq(AnalysisResultCoreEntity::getTaskId, taskId)
        );
        if (core != null) {
            core.setStatus(TASK_STATUS_PROCESSING);
            if (stageType != null && !stageType.isBlank()) {
                removeFailedDimension(core, normalizeFailedDimension(stageType));
            }
            coreMapper.updateById(core);
        }

        VideoAnalysisTaskEntity task = videoAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                        .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                        .isNull(VideoAnalysisTaskEntity::getDeletedAt)
        );
        if (task == null) {
            return;
        }
        task.setStatus(TASK_STATUS_PROCESSING);
        task.setProgress(Math.max(task.getProgress() == null ? 0 : task.getProgress(), 20));
        task.setCompletedAt(null);
        task.setErrorStep(null);
        task.setErrorMessage(null);
        task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        videoAnalysisTaskMapper.updateById(task);
    }

    public TimelineMatchResult getSavedFatTimeline(String taskId) {
        AnalysisResultTimelineAssetEntity timelineAsset = findActiveTimelineAsset(taskId);
        if (timelineAsset == null || timelineAsset.getFatTimelineJson() == null || timelineAsset.getFatTimelineJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(timelineAsset.getFatTimelineJson(), TimelineMatchResult.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "fatTimelineJson 解析失败: " + ex.getMessage());
        }
    }

    private void upsertCore(AnalysisResultWriteCommand command) {
        AnalysisResultCoreEntity entity = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>().eq(AnalysisResultCoreEntity::getTaskId, command.getTaskId())
        );
        if (entity == null) {
            entity = new AnalysisResultCoreEntity();
            entity.setBizId(command.getBizId());
            entity.setTaskId(command.getTaskId());
            fillCoreFields(entity, command);
            coreMapper.insert(entity);
            return;
        }
        fillCoreFields(entity, command);
        coreMapper.updateById(entity);
    }

    private void fillCoreFields(AnalysisResultCoreEntity entity, AnalysisResultWriteCommand command) {
        entity.setStatus(Optional.ofNullable(command.getStatus()).orElse(TASK_STATUS_COMPLETED));
        entity.setCategoryId(command.getCategoryId());
        entity.setLlmModelUsed(command.getLlmModelUsed());
        entity.setSchemaVersion(Optional.ofNullable(command.getSchemaVersion()).orElse("v2"));
        entity.setPartialFailedDimensions(toJsonSafely(command.getPartialFailedDimensions()));
        entity.setDurationSec(command.getDurationSec());
        entity.setWidth(command.getWidth());
        entity.setHeight(command.getHeight());
        entity.setFps(command.getFps());
        entity.setVideoCodec(command.getVideoCodec());
        entity.setAudioCodec(command.getAudioCodec());
        entity.setHasAudio(command.getHasAudio());
        entity.setBitrate(command.getBitrate());
        entity.setShotCount(Optional.ofNullable(command.getShotCount()).orElse(0));
        entity.setAsrSegmentCount(Optional.ofNullable(command.getAsrSegmentCount()).orElse(0));
        entity.setKeyFrameCount(Optional.ofNullable(command.getKeyFrameCount()).orElse(0));
    }

    private void fillProbeFields(AnalysisResultCoreEntity entity, MediaInfo mediaInfo) {
        if (mediaInfo == null) {
            return;
        }
        entity.setDurationSec(mediaInfo.getDuration() == null
                ? null
                : BigDecimal.valueOf(mediaInfo.getDuration()));
        entity.setWidth(mediaInfo.getWidth());
        entity.setHeight(mediaInfo.getHeight());
        entity.setFps(mediaInfo.getFps() == null
                ? null
                : BigDecimal.valueOf(mediaInfo.getFps()));
        entity.setVideoCodec(mediaInfo.getCodec());
        entity.setAudioCodec(mediaInfo.getAudioCodec());
        entity.setHasAudio(mediaInfo.getHasAudio() == null ? null : (mediaInfo.getHasAudio() ? 1 : 0));
        entity.setBitrate(mediaInfo.getBitrate());
    }

    private void upsertTextAsset(AnalysisResultWriteCommand command) {
        AnalysisResultTextAssetEntity entity = textAssetMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultTextAssetEntity>().eq(AnalysisResultTextAssetEntity::getTaskId, command.getTaskId())
        );
        if (entity == null) {
            entity = new AnalysisResultTextAssetEntity();
            entity.setBizId(command.getBizId());
            entity.setTaskId(command.getTaskId());
            fillTextFields(entity, command);
            textAssetMapper.insert(entity);
            return;
        }
        fillTextFields(entity, command);
        textAssetMapper.updateById(entity);
    }

    private void fillTextFields(AnalysisResultTextAssetEntity entity, AnalysisResultWriteCommand command) {
        entity.setAsrFullText(command.getAsrFullText());
        entity.setOcrFullText(command.getOcrFullText());
        entity.setTranscriptSummaryText(command.getTranscriptSummaryText());
        entity.setKeywordsText(command.getKeywordsText());
    }

    private void upsertTimelineAsset(AnalysisResultWriteCommand command) {
        AnalysisResultTimelineAssetEntity existingEntity = findActiveTimelineAsset(command.getTaskId());

        if (existingEntity == null) {
            AnalysisResultTimelineAssetEntity newEntity = new AnalysisResultTimelineAssetEntity();
            newEntity.setBizId(command.getBizId());
            newEntity.setTaskId(command.getTaskId());
            fillTimelineFields(newEntity, command);
            try {
                timelineAssetMapper.insert(newEntity);
                return;
            } catch (DuplicateKeyException duplicateKeyException) {
                // 并发写入场景：另一个事务已插入成功，回查后切换为 update，保证同 taskId 幂等。
                log.warn("timeline asset concurrent insert detected, fallback to update: taskId={}", command.getTaskId());
                existingEntity = findActiveTimelineAsset(command.getTaskId());
                if (existingEntity == null) {
                    throw duplicateKeyException;
                }
            }
        }

        fillTimelineFields(existingEntity, command);
        existingEntity.setUpdatedAt(LocalDateTime.now());
        timelineAssetMapper.updateById(existingEntity);
    }

    private void fillTimelineFields(AnalysisResultTimelineAssetEntity entity, AnalysisResultWriteCommand command) {
        if (command.getLlmTokenUsageJson() != null) {
            entity.setLlmTokenUsageJson(command.getLlmTokenUsageJson());
        }
        if (command.getProvenanceJson() != null) {
            entity.setProvenanceJson(command.getProvenanceJson());
        }
        if (command.getFatTimelineJson() != null) {
            entity.setFatTimelineJson(command.getFatTimelineJson());
        }
        if (command.getRefinedTimelineJson() != null) {
            entity.setRefinedTimelineJson(command.getRefinedTimelineJson());
        }
        if (command.getVideoStructureTemplateJson() != null) {
            entity.setVideoStructureTemplateJson(command.getVideoStructureTemplateJson());
        }
    }

    private Map<String, Object> buildVideoInfo(AnalysisResultCoreEntity core) {
        Map<String, Object> videoInfo = new LinkedHashMap<>();
        videoInfo.put("duration", core.getDurationSec());
        videoInfo.put("width", core.getWidth());
        videoInfo.put("height", core.getHeight());
        videoInfo.put("fps", core.getFps());
        videoInfo.put("codec", core.getVideoCodec());
        videoInfo.put("audioCodec", core.getAudioCodec());
        videoInfo.put("hasAudio", core.getHasAudio() != null && core.getHasAudio() == 1);
        videoInfo.put("bitrate", core.getBitrate());
        return videoInfo;
    }

    private List<VideoTaskResultResponse.TranscriptItem> buildTranscript(List<AsrSegmentEntity> segments) {
        List<VideoTaskResultResponse.TranscriptItem> transcript = new ArrayList<>();
        for (AsrSegmentEntity segment : segments) {
            VideoTaskResultResponse.TranscriptItem item = new VideoTaskResultResponse.TranscriptItem();
            item.setStartTime(segment.getStartTime() == null ? null : segment.getStartTime().doubleValue());
            item.setEndTime(segment.getEndTime() == null ? null : segment.getEndTime().doubleValue());
            item.setText(segment.getText());
            item.setSpeaker(segment.getSpeakerLabel());
            item.setConfidence(segment.getConfidence() == null ? null : segment.getConfidence().doubleValue());
            item.setAudioEmotion(segment.getAudioEmotion());
            item.setVolumeIntensity(segment.getVolumeIntensity());
            item.setBackgroundEnvironment(segment.getBackgroundEnvironment());
            item.setVocalVibe(segment.getVocalVibe());
            item.setBgmGenre(segment.getBgmGenre());
            item.setBgmInstruments(segment.getBgmInstruments());
            transcript.add(item);
        }
        return transcript;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "partialFailedDimensions JSON 解析失败: " + ex.getMessage());
        }
    }

    private Object parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 解析失败: " + ex.getMessage());
        }
    }

    private String toJsonSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败: " + ex.getMessage());
        }
    }

    private void addFailedDimension(AnalysisResultCoreEntity core, String dimension) {
        Set<String> failedSet = new HashSet<>(parseJsonArray(core.getPartialFailedDimensions()));
        failedSet.add(dimension);
        core.setPartialFailedDimensions(toJsonSafely(failedSet.stream().sorted().toList()));
    }

    private void removeFailedDimension(AnalysisResultCoreEntity core, String dimension) {
        List<String> failed = new ArrayList<>(parseJsonArray(core.getPartialFailedDimensions()));
        failed.removeIf(item -> item.equalsIgnoreCase(dimension));
        core.setPartialFailedDimensions(failed.isEmpty() ? null : toJsonSafely(failed));
    }

    private String normalizeFailedDimension(String stageType) {
        return stageType == null ? "" : stageType.trim().toLowerCase();
    }

    private int resolveSegmentIndex(Integer preferredIndex, int fallbackIndex, Set<Integer> usedIndexes) {
        int index = preferredIndex == null ? fallbackIndex : preferredIndex;
        while (usedIndexes.contains(index)) {
            index++;
        }
        usedIndexes.add(index);
        return index;
    }

    private AnalysisResultTimelineAssetEntity findActiveTimelineAsset(String taskId) {
        return timelineAssetMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultTimelineAssetEntity>()
                        .eq(AnalysisResultTimelineAssetEntity::getTaskId, taskId)
                        .isNull(AnalysisResultTimelineAssetEntity::getDeletedAt)
        );
    }
}
