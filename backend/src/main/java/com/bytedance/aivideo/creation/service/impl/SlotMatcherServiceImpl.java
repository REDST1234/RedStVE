package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.creation.dto.SlotMatchLlmResult;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.entity.SlotMatchResultEntity;
import com.bytedance.aivideo.creation.mapper.SlotMatchResultMapper;
import com.bytedance.aivideo.creation.service.CreationProjectService;
import com.bytedance.aivideo.creation.service.CreativeMaterialService;
import com.bytedance.aivideo.creation.service.SlotMatchLlmService;
import com.bytedance.aivideo.creation.service.SlotMatcherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * LLM 槽位匹配与素材缺口识别服务。
 */
@Slf4j
@Service
public class SlotMatcherServiceImpl implements SlotMatcherService {

    private static final String PROJECT_STATUS_MATCHING = "MATCHING";
    private static final String MATCH_STATUS_MISSING = "MISSING";
    private static final String MATCH_STATUS_VETOED = "VETOED";

    private final CreationProjectService creationProjectService;
    private final CreativeMaterialService creativeMaterialService;
    private final SlotMatchResultMapper slotMatchResultMapper;
    private final SlotMatchLlmService slotMatchLlmService;
    private final ObjectMapper objectMapper;

    public SlotMatcherServiceImpl(
            CreationProjectService creationProjectService,
            CreativeMaterialService creativeMaterialService,
            SlotMatchResultMapper slotMatchResultMapper,
            SlotMatchLlmService slotMatchLlmService,
            ObjectMapper objectMapper
    ) {
        this.creationProjectService = creationProjectService;
        this.creativeMaterialService = creativeMaterialService;
        this.slotMatchResultMapper = slotMatchResultMapper;
        this.slotMatchLlmService = slotMatchLlmService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String matchSlots(String projectId, String versionId) {
        long start = System.currentTimeMillis();
        CreationProjectEntity project = creationProjectService.requireActiveProject(projectId);
        if (project.getTemplateSnapshotJson() == null || project.getTemplateSnapshotJson().isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "项目未绑定模板快照，无法匹配");
        }

        String resolvedVersionId = resolveVersionId(versionId);
        List<CreativeMaterialEntity> materials = creativeMaterialService.listProjectAssets(projectId);
        if (materials.isEmpty()) {
            throw new BizException(ErrorCode.MATERIAL_NOT_FOUND, "项目未上传素材，无法匹配");
        }
        List<CreativeMaterialEntity> notProfiled = materials.stream()
                .filter(item -> !"PROFILED".equalsIgnoreCase(item.getStatus()))
                .toList();
        if (!notProfiled.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "存在未完成素材分析的素材，无法开始缺口识别");
        }

        ArrayNode segments = parseTemplateSegments(project.getTemplateSnapshotJson());
        SlotMatchLlmResult llmResult = slotMatchLlmService.matchSlots(
                projectId,
                resolvedVersionId,
                project.getTemplateSnapshotJson(),
                materials
        );

        slotMatchResultMapper.delete(new LambdaQueryWrapper<SlotMatchResultEntity>()
                .eq(SlotMatchResultEntity::getProjectId, projectId)
                .eq(SlotMatchResultEntity::getVersionId, resolvedVersionId)
                .isNull(SlotMatchResultEntity::getDeletedAt));

        int missingOrVetoedCount = 0;
        for (SlotMatchLlmResult.Decision decision : llmResult.getMatchDecisions()) {
            SlotMatchResultEntity row = new SlotMatchResultEntity();
            row.setMatchId("mtc_" + UUID.randomUUID().toString().replace("-", ""));
            row.setProjectId(projectId);
            row.setSegmentIndex(decision.getSegmentIndex());
            row.setSegmentRole(normalizeRole(decision.getSegmentRole()));
            row.setMatchedAssetId(blankToNull(decision.getMatchedAssetId()));
            row.setMatchedHighlightId(blankToNull(decision.getMatchedHighlightId()));
            row.setMatchScore(decision.getMatchScore());
            row.setMatchStatus(decision.getMatchStatus());
            row.setMatchReason(decision.getMatchReason());
            row.setVetoReason(decision.getVetoReason());
            row.setVersionId(resolvedVersionId);
            row.setAdaptationPlanJson(decision.getAdaptationPlan() == null ? "{}" : decision.getAdaptationPlan().toString());
            if (MATCH_STATUS_MISSING.equals(decision.getMatchStatus()) || MATCH_STATUS_VETOED.equals(decision.getMatchStatus())) {
                missingOrVetoedCount++;
            }
            slotMatchResultMapper.insert(row);
        }

        project.setStatus(PROJECT_STATUS_MATCHING);
        creationProjectService.updateById(project);
        log.info("slot match llm finished: projectId={}, versionId={}, templateSegmentCount={}, decisionCount={}, missingOrVetoedCount={}, elapsedMs={}",
                projectId, resolvedVersionId, segments.size(), llmResult.getMatchDecisions().size(), missingOrVetoedCount, System.currentTimeMillis() - start);
        return resolvedVersionId;
    }

    private ArrayNode parseTemplateSegments(String templateSnapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(templateSnapshotJson);
            JsonNode segmentsNode = root.path("scriptStructure").path("segments");
            if (segmentsNode.isArray() && !segmentsNode.isEmpty()) {
                return (ArrayNode) segmentsNode;
            }
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "模板快照 JSON 解析失败: " + ex.getMessage());
        }
        ArrayNode fallback = objectMapper.createArrayNode();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("segmentIndex", 0);
        body.put("role", "body");
        fallback.add(body);
        return fallback;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "body";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if ("hook".equals(normalized) || "body".equals(normalized) || "climax".equals(normalized) || "outro".equals(normalized)) {
            return normalized;
        }
        return "body";
    }

    private String resolveVersionId(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return "ver_" + UUID.randomUUID().toString().replace("-", "");
        }
        return versionId.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
