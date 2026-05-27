package com.bytedance.aivideo.deconstruct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.dto.ScriptwriterRunRequest;
import com.bytedance.aivideo.deconstruct.dto.ScriptwriterRunResponse;
import com.bytedance.aivideo.deconstruct.entity.DeconstructProjectMaterialEntity;
import com.bytedance.aivideo.deconstruct.entity.ProjectTemplateSnapshotEntity;
import com.bytedance.aivideo.deconstruct.entity.ScriptwriterRunRecordEntity;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructProjectMaterialMapper;
import com.bytedance.aivideo.deconstruct.mapper.ScriptwriterRunRecordMapper;
import com.bytedance.aivideo.video.entity.AnalysisResultTextAssetEntity;
import com.bytedance.aivideo.video.mapper.AnalysisResultTextAssetMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 编剧编排服务（快照驱动）。
 */
@Service
public class ScriptwriterOrchestratorService {

    private static final String RUN_STATUS_COMPLETED = "COMPLETED";

    private final ProjectTemplateSnapshotService projectTemplateSnapshotService;
    private final DeconstructProjectMaterialMapper deconstructProjectMaterialMapper;
    private final AnalysisResultTextAssetMapper analysisResultTextAssetMapper;
    private final ScriptwriterRunRecordMapper scriptwriterRunRecordMapper;
    private final ObjectMapper objectMapper;

    public ScriptwriterOrchestratorService(
            ProjectTemplateSnapshotService projectTemplateSnapshotService,
            DeconstructProjectMaterialMapper deconstructProjectMaterialMapper,
            AnalysisResultTextAssetMapper analysisResultTextAssetMapper,
            ScriptwriterRunRecordMapper scriptwriterRunRecordMapper,
            ObjectMapper objectMapper
    ) {
        this.projectTemplateSnapshotService = projectTemplateSnapshotService;
        this.deconstructProjectMaterialMapper = deconstructProjectMaterialMapper;
        this.analysisResultTextAssetMapper = analysisResultTextAssetMapper;
        this.scriptwriterRunRecordMapper = scriptwriterRunRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptwriterRunResponse run(String projectId, ScriptwriterRunRequest request) {
        ProjectTemplateSnapshotEntity snapshot = projectTemplateSnapshotService.resolveSnapshot(
                projectId,
                request == null ? null : request.getSnapshotId()
        );

        List<String> materialBizIds = resolveMaterialBizIds(projectId, request);
        String finalPrompt = assemblePrompt(snapshot, materialBizIds);

        Map<String, Object> writerOutput = buildDraftWriterOutput(snapshot, materialBizIds);
        String outputJson = toJson(writerOutput);
        String materialJson = toJson(materialBizIds);

        ScriptwriterRunRecordEntity record = new ScriptwriterRunRecordEntity();
        record.setRunId("run_" + UUID.randomUUID().toString().replace("-", ""));
        record.setProjectId(projectId.trim());
        record.setSnapshotId(snapshot.getSnapshotId());
        record.setMaterialBizIdsJson(materialJson);
        record.setFinalPromptText(finalPrompt);
        record.setStatus(RUN_STATUS_COMPLETED);
        record.setWriterOutputJson(outputJson);
        scriptwriterRunRecordMapper.insert(record);

        return toResponse(record, writerOutput, materialBizIds);
    }

    private List<String> resolveMaterialBizIds(String projectId, ScriptwriterRunRequest request) {
        if (request != null && request.getMaterialBizIds() != null && !request.getMaterialBizIds().isEmpty()) {
            return request.getMaterialBizIds().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }

        List<DeconstructProjectMaterialEntity> relations = deconstructProjectMaterialMapper.selectList(
                new LambdaQueryWrapper<DeconstructProjectMaterialEntity>()
                        .eq(DeconstructProjectMaterialEntity::getProjectId, projectId.trim())
                        .isNull(DeconstructProjectMaterialEntity::getDeletedAt)
                        .orderByAsc(DeconstructProjectMaterialEntity::getSortOrder)
                        .orderByDesc(DeconstructProjectMaterialEntity::getCreatedAt)
        );
        return relations.stream()
                .map(DeconstructProjectMaterialEntity::getMaterialBizId)
                .filter(value -> value != null && value > 0)
                .map(String::valueOf)
                .distinct()
                .toList();
    }

    private String assemblePrompt(ProjectTemplateSnapshotEntity snapshot, List<String> materialBizIds) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名短视频编剧，请基于模板快照与素材事实生成编排脚本。\n\n");
        prompt.append("[模板快照ID]\n").append(snapshot.getSnapshotId()).append("\n\n");
        prompt.append("[模板结构JSON]\n").append(snapshot.getSnapshotJson()).append("\n\n");
        prompt.append("[素材事实]\n");

        List<String> taskIds = deconstructProjectMaterialMapper.selectList(
                        new LambdaQueryWrapper<DeconstructProjectMaterialEntity>()
                                .eq(DeconstructProjectMaterialEntity::getProjectId, snapshot.getProjectId())
                                .isNull(DeconstructProjectMaterialEntity::getDeletedAt)
                ).stream()
                .filter(item -> item.getTaskId() != null && !item.getTaskId().isBlank())
                .filter(item -> materialBizIds.contains(String.valueOf(item.getMaterialBizId())))
                .map(DeconstructProjectMaterialEntity::getTaskId)
                .distinct()
                .toList();

        if (taskIds.isEmpty()) {
            prompt.append("- 未提供素材分析事实，请仅依据模板编排。\n");
            return prompt.toString();
        }

        List<AnalysisResultTextAssetEntity> textAssets = analysisResultTextAssetMapper.selectList(
                new LambdaQueryWrapper<AnalysisResultTextAssetEntity>()
                        .in(AnalysisResultTextAssetEntity::getTaskId, taskIds)
                        .isNull(AnalysisResultTextAssetEntity::getDeletedAt)
        );
        Map<String, AnalysisResultTextAssetEntity> textAssetMap = textAssets.stream()
                .collect(Collectors.toMap(AnalysisResultTextAssetEntity::getTaskId, item -> item, (a, b) -> a));

        for (String taskId : taskIds) {
            AnalysisResultTextAssetEntity textAsset = textAssetMap.get(taskId);
            if (textAsset == null) {
                prompt.append("- [task=").append(taskId).append("] 无文本事实\n");
                continue;
            }
            String asr = normalizeFact(textAsset.getAsrFullText());
            String summary = normalizeFact(textAsset.getTranscriptSummaryText());
            String keywords = normalizeFact(textAsset.getKeywordsText());
            prompt.append("- [task=").append(taskId).append("]\n");
            prompt.append("  asrFullText: ").append(asr).append("\n");
            prompt.append("  summary: ").append(summary).append("\n");
            prompt.append("  keywords: ").append(keywords).append("\n");
        }
        return prompt.toString();
    }

    private Map<String, Object> buildDraftWriterOutput(ProjectTemplateSnapshotEntity snapshot, List<String> materialBizIds) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mode", "snapshot_driven_draft");
        output.put("snapshotId", snapshot.getSnapshotId());
        output.put("templateId", snapshot.getTemplateId());
        output.put("templateVersion", snapshot.getTemplateVersion());
        output.put("materialCount", materialBizIds.size());

        List<Map<String, Object>> beatPlan = new ArrayList<>();
        beatPlan.add(Map.of("phase", "HOOK", "goal", "用高冲击镜头开场", "durationHint", "0-15%"));
        beatPlan.add(Map.of("phase", "BODY", "goal", "递进信息密度与节奏", "durationHint", "15-85%"));
        beatPlan.add(Map.of("phase", "CTA", "goal", "完成收束与行动引导", "durationHint", "85-100%"));
        output.put("beatPlan", beatPlan);
        return output;
    }

    private ScriptwriterRunResponse toResponse(
            ScriptwriterRunRecordEntity record,
            Map<String, Object> writerOutput,
            List<String> materialBizIds
    ) {
        ScriptwriterRunResponse response = new ScriptwriterRunResponse();
        response.setRunId(record.getRunId());
        response.setProjectId(record.getProjectId());
        response.setSnapshotId(record.getSnapshotId());
        response.setStatus(record.getStatus());
        response.setMaterialBizIds(materialBizIds);
        response.setFinalPromptText(record.getFinalPromptText());
        response.setWriterOutput(writerOutput);
        response.setCreatedAt(toUtc(record.getCreatedAt()));
        return response;
    }

    private OffsetDateTime toUtc(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.ofHours(8)).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private String normalizeFact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败: " + ex.getMessage());
        }
    }

    public ScriptwriterRunResponse getRunResult(String projectId, String runId) {
        if (projectId == null || projectId.isBlank() || runId == null || runId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "projectId/runId 不能为空");
        }
        ScriptwriterRunRecordEntity record = scriptwriterRunRecordMapper.selectOne(
                new LambdaQueryWrapper<ScriptwriterRunRecordEntity>()
                        .eq(ScriptwriterRunRecordEntity::getProjectId, projectId.trim())
                        .eq(ScriptwriterRunRecordEntity::getRunId, runId.trim())
                        .isNull(ScriptwriterRunRecordEntity::getDeletedAt)
        );
        if (record == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "运行记录不存在: " + runId);
        }
        List<String> materialBizIds = parseMaterialIds(record.getMaterialBizIdsJson());
        Map<String, Object> writerOutput = parseWriterOutput(record.getWriterOutputJson());
        return toResponse(record, writerOutput, materialBizIds);
    }

    private List<String> parseMaterialIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> parseWriterOutput(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of("raw", json);
        }
    }
}
