package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.engine.asr.model.AsrSegmentResult;
import com.bytedance.aivideo.engine.asr.model.AsrTranscriptionResult;
import com.bytedance.aivideo.video.dto.AnalysisResultWriteCommand;
import com.bytedance.aivideo.video.dto.MediaInfo;
import com.bytedance.aivideo.video.dto.VideoTaskResultResponse;
import com.bytedance.aivideo.video.entity.AnalysisResultCoreEntity;
import com.bytedance.aivideo.video.entity.AnalysisResultTextAssetEntity;
import com.bytedance.aivideo.video.entity.AnalysisResultTimelineAssetEntity;
import com.bytedance.aivideo.video.entity.AsrSegmentEntity;
import com.bytedance.aivideo.video.mapper.AnalysisResultCoreMapper;
import com.bytedance.aivideo.video.mapper.AnalysisResultTextAssetMapper;
import com.bytedance.aivideo.video.mapper.AnalysisResultTimelineAssetMapper;
import com.bytedance.aivideo.video.mapper.AsrSegmentMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
@Service
public class VideoAnalysisResultService {

    private final AnalysisResultCoreMapper coreMapper;
    private final AnalysisResultTextAssetMapper textAssetMapper;
    private final AnalysisResultTimelineAssetMapper timelineAssetMapper;
    private final AsrSegmentMapper asrSegmentMapper;
    private final ObjectMapper objectMapper;
    private static final String TASK_STATUS_PROCESSING = "PROCESSING";
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    private static final String TASK_STATUS_FAILED = "FAILED";

    public VideoAnalysisResultService(
            AnalysisResultCoreMapper coreMapper,
            AnalysisResultTextAssetMapper textAssetMapper,
            AnalysisResultTimelineAssetMapper timelineAssetMapper,
            AsrSegmentMapper asrSegmentMapper,
            ObjectMapper objectMapper
    ) {
        this.coreMapper = coreMapper;
        this.textAssetMapper = textAssetMapper;
        this.timelineAssetMapper = timelineAssetMapper;
        this.asrSegmentMapper = asrSegmentMapper;
        this.objectMapper = objectMapper;
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

    public VideoTaskResultResponse getTaskResult(String taskId, boolean includeTimeline) {
        AnalysisResultCoreEntity core = coreMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultCoreEntity>().eq(AnalysisResultCoreEntity::getTaskId, taskId)
        );
        if (core == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "任务结果不存在: " + taskId);
        }

        AnalysisResultTextAssetEntity textAsset = textAssetMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultTextAssetEntity>().eq(AnalysisResultTextAssetEntity::getTaskId, taskId)
        );

        AnalysisResultTimelineAssetEntity timelineAsset = null;
        if (includeTimeline) {
            timelineAsset = timelineAssetMapper.selectOne(
                    new LambdaQueryWrapper<AnalysisResultTimelineAssetEntity>().eq(AnalysisResultTimelineAssetEntity::getTaskId, taskId)
            );
        }

        List<AsrSegmentEntity> asrSegments = asrSegmentMapper.selectList(
                new LambdaQueryWrapper<AsrSegmentEntity>()
                        .eq(AsrSegmentEntity::getTaskId, taskId)
                        .orderByAsc(AsrSegmentEntity::getSegmentIndex)
        );

        VideoTaskResultResponse response = new VideoTaskResultResponse();
        response.setTaskId(taskId);
        response.setStatus(core.getStatus());
        response.setVideoInfo(buildVideoInfo(core));
        response.setCategoryId(core.getCategoryId());
        response.setPartialFailedDimensions(parseJsonArray(core.getPartialFailedDimensions()));
        response.setTranscript(buildTranscript(asrSegments));

        if (includeTimeline && timelineAsset != null) {
            response.setTimelineLog(parseJsonObject(timelineAsset.getTimelineLogJson()));

            VideoTaskResultResponse.LlmAnalysis llmAnalysis = new VideoTaskResultResponse.LlmAnalysis();
            llmAnalysis.setScript(parseJsonObject(timelineAsset.getScriptStructureJson()));
            llmAnalysis.setRhythm(parseJsonObject(timelineAsset.getRhythmStructureJson()));
            llmAnalysis.setPackaging(parseJsonObject(timelineAsset.getPackagingStructureJson()));
            response.setLlmAnalysis(llmAnalysis);
        } else {
            response.setTimelineLog(null);
            response.setLlmAnalysis(null);
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

        asrSegmentMapper.delete(new LambdaQueryWrapper<AsrSegmentEntity>().eq(AsrSegmentEntity::getTaskId, taskId));
        int segmentIndex = 0;
        for (AsrSegmentResult segment : transcriptionResult.getSegments()) {
            AsrSegmentEntity entity = new AsrSegmentEntity();
            entity.setTaskId(taskId);
            entity.setSegmentIndex(segment.getSegmentIndex() == null ? segmentIndex : segment.getSegmentIndex());
            entity.setText(segment.getText());
            entity.setStartTime(BigDecimal.valueOf(segment.getStartSec()));
            entity.setEndTime(BigDecimal.valueOf(segment.getEndSec()));
            entity.setSpeakerLabel(segment.getSpeakerLabel());
            entity.setConfidence(segment.getConfidence() == null ? null : BigDecimal.valueOf(segment.getConfidence()));
            asrSegmentMapper.insert(entity);
            segmentIndex++;
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
        AnalysisResultTimelineAssetEntity entity = timelineAssetMapper.selectOne(
                new LambdaQueryWrapper<AnalysisResultTimelineAssetEntity>().eq(AnalysisResultTimelineAssetEntity::getTaskId, command.getTaskId())
        );
        if (entity == null) {
            entity = new AnalysisResultTimelineAssetEntity();
            entity.setBizId(command.getBizId());
            entity.setTaskId(command.getTaskId());
            fillTimelineFields(entity, command);
            timelineAssetMapper.insert(entity);
            return;
        }
        fillTimelineFields(entity, command);
        timelineAssetMapper.updateById(entity);
    }

    private void fillTimelineFields(AnalysisResultTimelineAssetEntity entity, AnalysisResultWriteCommand command) {
        entity.setShotSummaryJson(command.getShotSummaryJson());
        entity.setTimelineLogJson(command.getTimelineLogJson());
        entity.setScriptStructureJson(command.getScriptStructureJson());
        entity.setRhythmStructureJson(command.getRhythmStructureJson());
        entity.setPackagingStructureJson(command.getPackagingStructureJson());
        entity.setLlmTokenUsageJson(command.getLlmTokenUsageJson());
        entity.setProvenanceJson(command.getProvenanceJson());
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
}
