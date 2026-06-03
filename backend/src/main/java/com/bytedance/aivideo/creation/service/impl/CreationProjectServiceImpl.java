package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.entity.CreationProjectBgmBindingEntity;
import com.bytedance.aivideo.creation.entity.SlotMatchResultEntity;
import com.bytedance.aivideo.creation.mapper.CreationProjectMapper;
import com.bytedance.aivideo.creation.mapper.SlotMatchResultMapper;
import com.bytedance.aivideo.creation.service.CreationProjectBgmBindingService;
import com.bytedance.aivideo.creation.service.CreationProjectService;
import com.bytedance.aivideo.creation.service.CreationTemplateSnapshotService;
import com.bytedance.aivideo.creation.mapper.CreativeMaterialMapper;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.entity.ProjectTemplateSnapshotEntity;
import com.bytedance.aivideo.deconstruct.service.DeconstructTemplateService;
import com.bytedance.aivideo.engine.remotion.RemotionServiceClient;
import com.bytedance.aivideo.engine.remotion.VideoOrchestrationService;
import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import com.bytedance.aivideo.engine.remotion.dto.VideoOrchestrationResult;
import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.creation.dto.remotion.BgmConfig;
import com.bytedance.aivideo.creation.dto.remotion.CanvasConfig;
import com.bytedance.aivideo.creation.util.BgmMixLevelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 创作项目服务实现。
 */
@Slf4j
@Service
public class CreationProjectServiceImpl extends ServiceImpl<CreationProjectMapper, CreationProjectEntity> implements CreationProjectService {

    private static final String PROJECT_STATUS_DRAFT = "DRAFT";
    private static final String ASPECT_RATIO_PORTRAIT = "9:16";
    private static final String ASPECT_RATIO_LANDSCAPE = "16:9";
    private static final String ASPECT_RATIO_SQUARE = "1:1";
    private static final String ASPECT_RATIO_FOUR_FIVE = "4:5";
    private static final int DEFAULT_PORTRAIT_WIDTH = 1080;
    private static final int DEFAULT_PORTRAIT_HEIGHT = 1920;
    private static final int DEFAULT_LANDSCAPE_WIDTH = 1920;
    private static final int DEFAULT_LANDSCAPE_HEIGHT = 1080;
    private static final int DEFAULT_SQUARE_SIZE = 1080;
    private static final int DEFAULT_FOUR_FIVE_WIDTH = 1080;
    private static final int DEFAULT_FOUR_FIVE_HEIGHT = 1350;
    private static final int DEFAULT_CANVAS_FPS = 30;
    private static final long ORCHESTRATION_CACHE_TTL_DAYS = 7;
    private static final String RENDER_TASK_KEY_PREFIX = "render:task:";
    private static final String RENDER_SCRIPT_KEY_PREFIX = "render:script:";
    private static final String RENDER_ORCHESTRATION_KEY_PREFIX = "render:orchestration:";
    private static final String STORAGE_DIR_NAME = "storage";

    private final DeconstructTemplateService deconstructTemplateService;
    private final CreationTemplateSnapshotService creationTemplateSnapshotService;
    private final VideoOrchestrationService videoOrchestrationService;
    private final RemotionServiceClient remotionServiceClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CreativeMaterialMapper creativeMaterialMapper;
    private final SlotMatchResultMapper slotMatchResultMapper;
    private final CreationProjectBgmBindingService creationProjectBgmBindingService;

    @Value("${remotion.service.url:http://localhost:3001}")
    private String remotionServiceUrl;

    public CreationProjectServiceImpl(
            DeconstructTemplateService deconstructTemplateService,
            CreationTemplateSnapshotService creationTemplateSnapshotService,
            VideoOrchestrationService videoOrchestrationService,
            RemotionServiceClient remotionServiceClient,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            CreativeMaterialMapper creativeMaterialMapper,
            SlotMatchResultMapper slotMatchResultMapper,
            CreationProjectBgmBindingService creationProjectBgmBindingService
    ) {
        this.deconstructTemplateService = deconstructTemplateService;
        this.creationTemplateSnapshotService = creationTemplateSnapshotService;
        this.videoOrchestrationService = videoOrchestrationService;
        this.remotionServiceClient = remotionServiceClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.creativeMaterialMapper = creativeMaterialMapper;
        this.slotMatchResultMapper = slotMatchResultMapper;
        this.creationProjectBgmBindingService = creationProjectBgmBindingService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreationProjectEntity createProject(String title, String description) {
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "title 不能为空");
        }
        CreationProjectEntity entity = new CreationProjectEntity();
        entity.setProjectId("crp_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setTitle(title.trim());
        entity.setDescription(description == null ? null : description.trim());
        entity.setStatus(PROJECT_STATUS_DRAFT);
        entity.setTemplateId(null);
        entity.setTemplateSnapshotId(null);
        entity.setTemplateSnapshotJson(null);
        this.baseMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreationProjectEntity bindTemplate(String projectId, String templateId, Integer templateVersion) {
        CreationProjectEntity project = requireActiveProject(projectId);
        DeconstructTemplateEntity template = deconstructTemplateService.getTemplateVersion(templateId, templateVersion);
        ProjectTemplateSnapshotEntity snapshot = creationTemplateSnapshotService.createOrActivate(project.getProjectId(), template);
        project.setTemplateId(snapshot.getTemplateId());
        project.setTemplateSnapshotId(snapshot.getSnapshotId());
        project.setTemplateSnapshotJson(snapshot.getSnapshotJson());
        this.baseMapper.updateById(project);
        return project;
    }

    @Override
    public CreationProjectEntity requireActiveProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "projectId 不能为空");
        }
        CreationProjectEntity project = this.baseMapper.selectOne(
                new LambdaQueryWrapper<CreationProjectEntity>()
                        .eq(CreationProjectEntity::getProjectId, projectId.trim())
                        .isNull(CreationProjectEntity::getDeletedAt)
                        .last("LIMIT 1")
        );
        if (project == null) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND, "创作项目不存在: " + projectId);
        }
        return project;
    }

    @Override
    public Page<CreationProjectEntity> listProjects(int page, int size, String keyword) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(size, 1);
        LambdaQueryWrapper<CreationProjectEntity> wrapper = new LambdaQueryWrapper<CreationProjectEntity>()
                .isNull(CreationProjectEntity::getDeletedAt)
                .orderByDesc(CreationProjectEntity::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(CreationProjectEntity::getTitle, trimmed)
                    .or()
                    .like(CreationProjectEntity::getDescription, trimmed));
        }
        return this.baseMapper.selectPage(new Page<>(normalizedPage, normalizedSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreationProjectEntity updateProjectBasics(String projectId, String title, String description) {
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "title 不能为空");
        }
        CreationProjectEntity entity = requireActiveProject(projectId);
        entity.setTitle(title.trim());
        entity.setDescription(description == null ? null : description.trim());
        this.baseMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(String projectId) {
        CreationProjectEntity entity = requireActiveProject(projectId);
        this.baseMapper.deleteById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateVideo(String projectId, String aspectRatio) {
        generateVideoInternal(projectId, false, aspectRatio);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void regenerateVideo(String projectId, String aspectRatio) {
        clearRenderCache(projectId);
        generateVideoInternal(projectId, true, aspectRatio);
    }

    private void generateVideoInternal(String projectId, boolean forceRefresh, String aspectRatio) {
        CreationProjectEntity project = requireActiveProject(projectId);
        String normalizedAspectRatio = normalizeAspectRatio(
                aspectRatio != null && !aspectRatio.isBlank() ? aspectRatio : project.getRenderAspectRatio()
        );
        if (normalizedAspectRatio != null && !normalizedAspectRatio.equals(project.getRenderAspectRatio())) {
            project.setRenderAspectRatio(normalizedAspectRatio);
        }
        String description = project.getDescription() != null && !project.getDescription().isBlank()
                ? project.getDescription() : project.getTitle();

        // 获取真实素材
        List<CreativeMaterialEntity> materials = creativeMaterialMapper.selectList(
                new LambdaQueryWrapper<CreativeMaterialEntity>()
                        .eq(CreativeMaterialEntity::getProjectId, projectId)
                        .eq(CreativeMaterialEntity::getStatus, "PROFILED")
                        .isNull(CreativeMaterialEntity::getDeletedAt)
        );

        CanvasConfig inferredCanvas = inferCanvasConfig(materials);
        CanvasConfig targetCanvas = resolveTargetCanvas(normalizedAspectRatio, inferredCanvas);
        CompositionScript script = forceRefresh ? null : loadRetryableScript(projectId, project, targetCanvas);
        if (script != null) {
            script = videoOrchestrationService.sanitizeScript(script);
            log.info("Reuse cached composition script for render retry: projectId={}, aspectRatio={}", projectId, normalizedAspectRatio);
        } else if (forceRefresh) {
            log.info("Force regenerate composition script via LLM: projectId={}, aspectRatio={}", projectId, normalizedAspectRatio);
        }

        CreationProjectBgmBindingEntity selectedBgm = creationProjectBgmBindingService.findCurrentBindingEntity(projectId);
        List<Map<String, Object>> assetList = buildRenderAssetList(projectId, materials);
        String templateBrief = buildTemplateBrief(project);
        String assetBrief = buildAssetBrief(assetList);
        String selectedBgmBrief = buildSelectedBgmBrief(selectedBgm);
        String canvasBrief = buildCanvasBrief(normalizedAspectRatio, targetCanvas);

        if (script == null) {
            // 调用 LLM 智能编排
            VideoOrchestrationResult orchestrationResult = videoOrchestrationService.orchestrateVideoResult(
                    description,
                    templateBrief,
                    assetBrief,
                    selectedBgmBrief,
                    canvasBrief
            );
            script = orchestrationResult.getScript();
            script.setProjectId(projectId);
            applySelectedBgm(script, selectedBgm);
            applyCanvas(script, targetCanvas);
            persistOrchestrationArtifacts(projectId, orchestrationResult, script);
        } else {
            applyCanvas(script, targetCanvas);
        }
        normalizeMediaSources(script);

        // 调用 Remotion 微服务
        RenderResponse response = remotionServiceClient.submitRenderTask(projectId, script);
        
        if ("FAILED".equals(response.getStatus())) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "提交渲染任务失败: " + response.getError());
        }

        // 更新项目状态
        project.setStatus("GENERATING");
        this.baseMapper.updateById(project);
    }

    private String buildSelectedBgmBrief(CreationProjectBgmBindingEntity selectedBgm) {
        if (selectedBgm == null) {
            return "当前项目未选择 BGM。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("当前项目已选 BGM：\n");
        appendBriefLine(sb, "audioId", selectedBgm.getAudioId());
        appendBriefLine(sb, "名称", selectedBgm.getAudioName());
        appendBriefLine(sb, "可用链接", toRemotionMediaSrc(selectedBgm.getSrcPath()));
        appendBriefLine(sb, "混音档位", selectedBgm.getMixLevel());
        appendBriefLine(sb, "是否循环", boolLabel(selectedBgm.getLoopEnabled()));
        appendBriefLine(sb, "淡入帧数", valueOrDash(selectedBgm.getFadeInFrames()));
        appendBriefLine(sb, "淡出帧数", valueOrDash(selectedBgm.getFadeOutFrames()));
        appendBriefLine(sb, "说明", "若你决定使用全局 BGM，必须严格复用上述可用链接作为 bgm.src。");
        return sb.toString().trim();
    }

    private List<Map<String, Object>> buildRenderAssetList(String projectId, List<CreativeMaterialEntity> materials) {
        List<Map<String, Object>> assetList = new ArrayList<>();
        Map<String, CreativeMaterialEntity> materialByBizId = new HashMap<>();
        if (materials != null) {
            for (CreativeMaterialEntity material : materials) {
                if (material == null) {
                    continue;
                }
                String bizId = String.valueOf(material.getBizId());
                materialByBizId.put(bizId, material);
                assetList.add(toOriginalRenderAsset(material, bizId));
            }
        }
        assetList.addAll(buildAdaptedRenderAssets(projectId, materialByBizId));
        return assetList;
    }

    private String buildTemplateBrief(CreationProjectEntity project) {
        if (project.getTemplateSnapshotJson() == null || project.getTemplateSnapshotJson().isBlank()) {
            return "未绑定模板：本次编排没有固定模板约束，请仅根据项目描述、素材卡片与 BGM 信息自由编排。";
        }
        try {
            JsonNode root = objectMapper.readTree(project.getTemplateSnapshotJson());
            StringBuilder sb = new StringBuilder("模板导演说明：\n");
                appendBriefLine(sb, "模板名称", text(root, "templateName"));
                appendBriefLine(sb, "模板品类", text(root, "category"));
                JsonNode meta = root.path("meta");
                if (meta.isObject()) {
                    JsonNode targetDuration = meta.path("targetDuration");
                    if (targetDuration.isObject()) {
                        String duration = formatRange(
                                valueText(targetDuration, "min"),
                                valueText(targetDuration, "max"),
                                valueText(targetDuration, "unit")
                        );
                        appendBriefLine(sb, "目标时长", duration);
                    }
                    appendBriefLine(sb, "画幅", text(meta, "aspectRatio"));
                    appendBriefLine(sb, "模板风格", joinArray(meta.path("styles"), "、", 5));
                appendBriefLine(sb, "模板说明", text(meta, "description"));
            }
            JsonNode scriptStructure = root.path("scriptStructure");
            if (scriptStructure.isObject()) {
                JsonNode segments = scriptStructure.path("segments");
                if (segments.isArray() && !segments.isEmpty()) {
                    sb.append("- 段落结构：\n");
                    for (JsonNode segment : segments) {
                        String role = text(segment, "role");
                        String label = text(segment, "label");
                        String desc = text(segment, "description");
                        String duration = "";
                        JsonNode durationRange = segment.path("durationRange");
                        if (durationRange.isObject()) {
                            duration = formatRange(
                                    valueText(durationRange, "min"),
                                    valueText(durationRange, "max"),
                                    "秒"
                            );
                        }
                        sb.append("  - segmentIndex=").append(valueText(segment, "segmentIndex"))
                                .append("，角色=").append(orDash(role))
                                .append("，名称=").append(orDash(label))
                                .append("，功能=").append(orDash(desc));
                        if (!duration.isBlank()) {
                            sb.append("，建议时长=").append(duration);
                        }
                        String visualHints = joinLabels("；",
                                nonBlankLabel("镜头偏好", joinArray(segment.path("preferredShotTypes"), "、", 5)),
                                nonBlankLabel("运镜偏好", joinArray(segment.path("preferredCameraMovements"), "、", 5)),
                                nonBlankLabel("视觉功能", joinArray(segment.path("requiredVisualFunctions"), "、", 6)),
                                nonBlankLabel("字幕策略", text(segment, "subtitleStrategy")),
                                nonBlankLabel("包装密度", text(segment, "packagingDensity")),
                                nonBlankLabel("缺口补位", joinArray(segment.path("fallbackStrategies"), "、", 5))
                        );
                        if (!visualHints.isBlank()) {
                            sb.append("，").append(visualHints);
                        }
                        sb.append('\n');
                    }
                }
            }
            JsonNode rhythm = root.path("rhythmStructure");
            if (rhythm.isObject()) {
                appendBriefLine(sb, "整体节奏", text(rhythm, "overallPace"));
                if (rhythm.hasNonNull("avgShotDuration")) {
                    appendBriefLine(sb, "平均镜头时长", valueText(rhythm, "avgShotDuration") + " 秒");
                }
                JsonNode climaxPositions = rhythm.path("climaxPositions");
                if (climaxPositions.isArray() && !climaxPositions.isEmpty()) {
                    List<String> climaxRanges = new ArrayList<>();
                    for (JsonNode node : climaxPositions) {
                        climaxRanges.add(rangeBetween(
                                valueText(node, "startPercent"),
                                valueText(node, "endPercent"),
                                "百分比区间"
                        ));
                    }
                    appendBriefLine(sb, "高潮区间", String.join("；", climaxRanges));
                }
                appendBriefLine(sb, "转场偏好", joinArray(rhythm.path("transitionStyles"), "、", 5));
            }
            JsonNode packaging = root.path("packagingStructure");
            if (packaging.isObject()) {
                appendBriefLine(sb, "字幕风格数量", arraySizeLabel(packaging.path("subtitleStyles")));
                appendBriefLine(sb, "标题卡数量", arraySizeLabel(packaging.path("titleCards")));
                appendBriefLine(sb, "包装转场数量", arraySizeLabel(packaging.path("transitions")));
            }
            JsonNode shots = root.path("shots");
            if (shots.isArray() && !shots.isEmpty()) {
                List<String> shotHints = new ArrayList<>();
                for (int i = 0; i < Math.min(4, shots.size()); i++) {
                    JsonNode shot = shots.get(i);
                    shotHints.add(joinLabels(" / ",
                            text(shot, "functionHint"),
                            text(shot, "shotTypeTag"),
                            text(shot, "cameraMovementTag"),
                            text(shot, "description")
                    ));
                }
                appendBriefLine(sb, "代表性镜头原型", String.join("；", shotHints));
            }
            appendBriefLine(sb, "执行要求", "请优先模仿该模板的段落职责、节奏和包装气质，而不是照搬字面内容。");
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to build template brief for project {}", project.getProjectId(), e);
            return "模板摘要构建失败：请仅根据项目描述、素材卡片与 BGM 信息自由编排。";
        }
    }

    private String buildAssetBrief(List<Map<String, Object>> assetList) {
        if (assetList == null || assetList.isEmpty()) {
            return "当前没有可用素材卡片。";
        }
        StringBuilder sb = new StringBuilder("可用素材导演卡片：\n");
        int cardIndex = 1;
        for (Map<String, Object> asset : assetList) {
            sb.append("素材卡片 ").append(cardIndex++).append("：\n");
            appendBriefLine(sb, "materialBizId", stringValue(asset.get("materialBizId")));
            appendBriefLine(sb, "素材类型", stringValue(asset.get("materialType")));
            appendBriefLine(sb, "版本类型", stringValue(asset.get("assetVariant")));
            appendBriefLine(sb, "最终可用链接", stringValue(asset.get("src")));
            if (asset.containsKey("sourceMaterialBizId")) {
                appendBriefLine(sb, "来源原素材", stringValue(asset.get("sourceMaterialBizId")));
            }
            if (asset.containsKey("originalSrc")) {
                appendBriefLine(sb, "原始素材链接", stringValue(asset.get("originalSrc")));
            }
            if (asset.containsKey("segmentIndex")) {
                appendBriefLine(sb, "适配目标段落", joinLabels("，",
                        "segmentIndex=" + stringValue(asset.get("segmentIndex")),
                        "segmentRole=" + stringValue(asset.get("segmentRole"))
                ));
            }
            if (asset.containsKey("adaptationSummary")) {
                appendBriefLine(sb, "已完成处理", stringValue(asset.get("adaptationSummary")));
            }
            if (asset.containsKey("priorityHint")) {
                appendBriefLine(sb, "使用建议", stringValue(asset.get("priorityHint")));
            }
            if (asset.containsKey("textContent")) {
                appendBriefLine(sb, "文本内容", stringValue(asset.get("textContent")));
            }
            if (asset.containsKey("profileSummary")) {
                appendBriefLine(sb, "内容摘要", stringValue(asset.get("profileSummary")));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private Map<String, Object> toOriginalRenderAsset(CreativeMaterialEntity material, String bizId) {
        Map<String, Object> asset = new HashMap<>();
        asset.put("materialBizId", bizId);
        asset.put("materialType", material.getMaterialType());
        asset.put("assetVariant", "ORIGINAL");

        if (material.getFilePath() != null && !material.getFilePath().isBlank()) {
            asset.put("src", toRemotionMediaSrc(material.getFilePath()));
        }
        if (material.getTextContent() != null && !material.getTextContent().isBlank()) {
            asset.put("textContent", material.getTextContent());
        }
        mergeProfile(asset, material);
        asset.put("priorityHint", "这是原始版素材，可作为回退候选；若存在对应的 ADAPTED 版本，应优先使用 ADAPTED。");
        return asset;
    }

    private List<Map<String, Object>> buildAdaptedRenderAssets(String projectId, Map<String, CreativeMaterialEntity> materialByBizId) {
        List<Map<String, Object>> adaptedAssets = new ArrayList<>();
        String latestVersionId = resolveLatestMatchedVersionId(projectId);
        if (latestVersionId == null) {
            return adaptedAssets;
        }
        List<SlotMatchResultEntity> rows = slotMatchResultMapper.selectList(
                new LambdaQueryWrapper<SlotMatchResultEntity>()
                        .eq(SlotMatchResultEntity::getProjectId, projectId)
                        .eq(SlotMatchResultEntity::getVersionId, latestVersionId)
                        .isNull(SlotMatchResultEntity::getDeletedAt)
                        .orderByAsc(SlotMatchResultEntity::getSegmentIndex)
        );
        for (SlotMatchResultEntity row : rows) {
            if (row == null
                    || row.getMatchedAssetId() == null
                    || row.getMatchedAssetId().isBlank()
                    || row.getAdaptedFilePath() == null
                    || row.getAdaptedFilePath().isBlank()) {
                continue;
            }
            CreativeMaterialEntity sourceMaterial = materialByBizId.get(row.getMatchedAssetId());
            if (sourceMaterial == null) {
                continue;
            }
            Map<String, Object> asset = new HashMap<>();
            asset.put("materialBizId", row.getMatchedAssetId() + "__adapted__seg_" + row.getSegmentIndex());
            asset.put("sourceMaterialBizId", row.getMatchedAssetId());
            asset.put("materialType", sourceMaterial.getMaterialType());
            asset.put("assetVariant", "ADAPTED");
            asset.put("segmentIndex", row.getSegmentIndex());
            asset.put("segmentRole", row.getSegmentRole());
            asset.put("matchStatus", row.getMatchStatus());
            asset.put("src", toRemotionMediaSrc(row.getAdaptedFilePath()));
            if (sourceMaterial.getFilePath() != null && !sourceMaterial.getFilePath().isBlank()) {
                asset.put("originalSrc", toRemotionMediaSrc(sourceMaterial.getFilePath()));
            }
            if (sourceMaterial.getTextContent() != null && !sourceMaterial.getTextContent().isBlank()) {
                asset.put("textContent", sourceMaterial.getTextContent());
            }
            mergeProfile(asset, sourceMaterial);
            asset.put("adaptationSummary", summarizeAdaptation(row.getAdaptationPlanJson()));
            asset.put("priorityHint", buildAdaptedPriorityHint(row));
            adaptedAssets.add(asset);
        }
        return adaptedAssets;
    }

    private String resolveLatestMatchedVersionId(String projectId) {
        SlotMatchResultEntity latest = slotMatchResultMapper.selectOne(
                new LambdaQueryWrapper<SlotMatchResultEntity>()
                        .eq(SlotMatchResultEntity::getProjectId, projectId)
                        .isNull(SlotMatchResultEntity::getDeletedAt)
                        .orderByDesc(SlotMatchResultEntity::getUpdatedAt)
                        .last("LIMIT 1")
        );
        if (latest == null || latest.getVersionId() == null || latest.getVersionId().isBlank()) {
            return null;
        }
        return latest.getVersionId();
    }

    private void mergeProfile(Map<String, Object> asset, CreativeMaterialEntity material) {
        if (material.getProfileJson() == null || material.getProfileJson().isBlank()) {
            return;
        }
        try {
            Map<String, Object> profile = objectMapper.readValue(material.getProfileJson(), new TypeReference<Map<String, Object>>() {});
            asset.put("profile", profile);
            asset.put("profileSummary", summarizeProfile(profile, material.getMaterialType()));
        } catch (Exception e) {
            log.warn("Failed to parse profileJson for material {}", material.getBizId(), e);
        }
    }

    private String summarizeProfile(Map<String, Object> profile, String materialType) {
        if (profile == null || profile.isEmpty()) {
            return "暂无额外画像信息。";
        }
        List<String> parts = new ArrayList<>();
        Object semanticTags = profile.get("semanticTags");
        if (semanticTags instanceof Map<?, ?> semanticMap) {
            Object mainEntities = semanticMap.get("mainEntities");
            if (mainEntities instanceof List<?> entities && !entities.isEmpty()) {
                parts.add("主体内容：" + entities.stream().map(String::valueOf).limit(4).collect(Collectors.joining("、")));
            }
            addIfPresent(parts, "整体风格", semanticMap.get("overallStyle"));
            addIfPresent(parts, "情绪", semanticMap.get("emotionTone"));
            addIfPresent(parts, "推荐段落", listToLabel(semanticMap.get("suitableRoles")));
            addIfPresent(parts, "声音环境", semanticMap.get("acousticEnvironment"));
        }
        Object physical = profile.get("physicalAttributes");
        if (physical instanceof Map<?, ?> physicalMap && "VIDEO".equalsIgnoreCase(materialType)) {
            Object lighting = physicalMap.get("lighting");
            if (lighting instanceof Map<?, ?> lightingMap) {
                addIfPresent(parts, "亮度诊断", lightingMap.get("diagnosisStrategy"));
            }
        }
        Object highlights = profile.get("highlights");
        if (highlights instanceof List<?> highlightList && !highlightList.isEmpty()) {
            Object first = highlightList.get(0);
            if (first instanceof Map<?, ?> firstHighlight) {
                addIfPresent(parts, "首个高光镜头", firstHighlight.get("shotType"));
                addIfPresent(parts, "首个运镜", firstHighlight.get("cameraMovement"));
                addIfPresent(parts, "首个高光文本", firstHighlight.get("textContent"));
            }
        }
        return parts.isEmpty() ? "暂无额外画像信息。" : String.join("；", parts);
    }

    private String summarizeAdaptation(String adaptationPlanJson) {
        if (adaptationPlanJson == null || adaptationPlanJson.isBlank()) {
            return "未记录具体适配处理。";
        }
        try {
            JsonNode root = objectMapper.readTree(adaptationPlanJson);
            JsonNode strategyChain = root.path("strategyChain");
            if (!strategyChain.isArray() || strategyChain.isEmpty()) {
                return "未记录具体适配处理。";
            }
            List<String> strategies = new ArrayList<>();
            for (JsonNode node : strategyChain) {
                String strategyType = text(node, "strategyType");
                if (strategyType != null && !strategyType.isBlank()) {
                    strategies.add(strategyType.toLowerCase(Locale.ROOT));
                }
            }
            if (strategies.isEmpty()) {
                return "未记录具体适配处理。";
            }
            return "已完成处理：" + String.join(" -> ", strategies);
        } catch (Exception e) {
            log.warn("Failed to summarize adaptation plan", e);
            return "未记录具体适配处理。";
        }
    }

    private String buildAdaptedPriorityHint(SlotMatchResultEntity row) {
        StringBuilder sb = new StringBuilder("这是优先成片候选。");
        if (row.getSegmentRole() != null && !row.getSegmentRole().isBlank()) {
            sb.append(" 对应模板 ").append(row.getSegmentRole()).append(" 段。");
        }
        if (row.getMatchReason() != null && !row.getMatchReason().isBlank()) {
            sb.append(" 匹配原因：").append(row.getMatchReason()).append('。');
        }
        sb.append(" 若当前场景目标与该段一致，请优先使用此 ADAPTED 版本，而不是回退到 ORIGINAL。");
        return sb.toString();
    }

    private void appendBriefLine(StringBuilder sb, String label, Object value) {
        String normalized = stringValue(value);
        if (normalized == null || normalized.isBlank() || "-".equals(normalized)) {
            return;
        }
        sb.append("- ").append(label).append("：").append(normalized).append('\n');
    }

    private String boolLabel(Boolean value) {
        if (value == null) {
            return "未知";
        }
        return value ? "是" : "否";
    }

    private String valueOrDash(Object value) {
        String normalized = stringValue(value);
        return normalized == null || normalized.isBlank() ? "-" : normalized;
    }

    private String formatRange(String min, String max, String unit) {
        String normalizedMin = stringValue(min);
        String normalizedMax = stringValue(max);
        String normalizedUnit = stringValue(unit);
        if (normalizedMin == null && normalizedMax == null) {
            return normalizedUnit == null ? "" : normalizedUnit;
        }
        if (normalizedMin != null && normalizedMax != null) {
            return normalizedMin + "-" + normalizedMax + (normalizedUnit == null ? "" : normalizedUnit);
        }
        String single = normalizedMin != null ? normalizedMin : normalizedMax;
        return single + (normalizedUnit == null ? "" : normalizedUnit);
    }

    private String rangeBetween(String start, String end, String suffix) {
        String normalizedStart = stringValue(start);
        String normalizedEnd = stringValue(end);
        String normalizedSuffix = stringValue(suffix);
        if (normalizedStart == null && normalizedEnd == null) {
            return normalizedSuffix == null ? "" : normalizedSuffix;
        }
        if (normalizedStart != null && normalizedEnd != null) {
            return normalizedStart + "到" + normalizedEnd + (normalizedSuffix == null ? "" : normalizedSuffix);
        }
        String single = normalizedStart != null ? normalizedStart : normalizedEnd;
        return single + (normalizedSuffix == null ? "" : normalizedSuffix);
    }

    private String joinLabels(String delimiter, String... parts) {
        List<String> filtered = new ArrayList<>();
        if (parts == null) {
            return "";
        }
        for (String part : parts) {
            String normalized = stringValue(part);
            if (normalized != null) {
                filtered.add(normalized);
            }
        }
        return filtered.isEmpty() ? "" : String.join(delimiter, filtered);
    }

    private String nonBlankLabel(String label, String value) {
        String normalized = stringValue(value);
        if (normalized == null) {
            return "";
        }
        return label + "=" + normalized;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        if (field.isValueNode()) {
            String value = field.asText();
            return value == null || value.isBlank() ? null : value.trim();
        }
        return field.toString();
    }

    private String valueText(JsonNode node, String fieldName) {
        return text(node, fieldName);
    }

    private String orDash(String value) {
        String normalized = stringValue(value);
        return normalized == null ? "-" : normalized;
    }

    private String joinArray(JsonNode arrayNode, String delimiter, int limit) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        int max = limit > 0 ? Math.min(limit, arrayNode.size()) : arrayNode.size();
        for (int i = 0; i < max; i++) {
            String value = stringValue(arrayNode.get(i).isValueNode() ? arrayNode.get(i).asText() : arrayNode.get(i).toString());
            if (value != null) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : String.join(delimiter, values);
    }

    private String arraySizeLabel(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return "0";
        }
        return String.valueOf(arrayNode.size());
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String listToLabel(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.joining("、"));
        }
        return stringValue(value);
    }

    private void addIfPresent(List<String> parts, String label, Object value) {
        String normalized = stringValue(value);
        if (normalized == null) {
            return;
        }
        parts.add(label + "：" + normalized);
    }

    private void applySelectedBgm(CompositionScript script, CreationProjectBgmBindingEntity selectedBgm) {
        if (script == null || selectedBgm == null) {
            return;
        }
        BgmConfig bgm = script.getBgm();
        if (bgm == null) {
            bgm = new BgmConfig();
            script.setBgm(bgm);
        }
        bgm.setSrc(toRemotionMediaSrc(selectedBgm.getSrcPath()));
        String finalMixLevel = bgm.getMixLevel() == null || bgm.getMixLevel().isBlank()
                ? selectedBgm.getMixLevel()
                : BgmMixLevelResolver.normalize(bgm.getMixLevel());
        bgm.setMixLevel(finalMixLevel);
        bgm.setVolume(BgmMixLevelResolver.resolveVolume(finalMixLevel));
        if (bgm.getLoop() == null) {
            bgm.setLoop(selectedBgm.getLoopEnabled());
        }
        if (bgm.getFadeInFrames() == null) {
            bgm.setFadeInFrames(selectedBgm.getFadeInFrames());
        }
        if (bgm.getFadeOutFrames() == null) {
            bgm.setFadeOutFrames(selectedBgm.getFadeOutFrames());
        }
    }

    private CanvasConfig inferCanvasConfig(List<CreativeMaterialEntity> materials) {
        if (materials != null) {
            for (CreativeMaterialEntity material : materials) {
                if (material == null || !"VIDEO".equalsIgnoreCase(material.getMaterialType())) {
                    continue;
                }
                Integer width = material.getWidth();
                Integer height = material.getHeight();
                if (width != null && width > 0 && height != null && height > 0) {
                    CanvasConfig canvas = new CanvasConfig();
                    canvas.setWidth(width);
                    canvas.setHeight(height);
                    canvas.setFps(DEFAULT_CANVAS_FPS);
                    return canvas;
                }
            }
        }

        CanvasConfig fallback = new CanvasConfig();
        fallback.setWidth(DEFAULT_LANDSCAPE_WIDTH);
        fallback.setHeight(DEFAULT_LANDSCAPE_HEIGHT);
        fallback.setFps(DEFAULT_CANVAS_FPS);
        return fallback;
    }

    private String normalizeAspectRatio(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return null;
        }
        String normalized = aspectRatio.trim();
        if (ASPECT_RATIO_PORTRAIT.equals(normalized)
                || ASPECT_RATIO_LANDSCAPE.equals(normalized)
                || ASPECT_RATIO_SQUARE.equals(normalized)
                || ASPECT_RATIO_FOUR_FIVE.equals(normalized)) {
            return normalized;
        }
        throw new BizException(ErrorCode.INVALID_REQUEST, "不支持的画面比例: " + aspectRatio);
    }

    private CanvasConfig resolveTargetCanvas(String aspectRatio, CanvasConfig inferredCanvas) {
        if (aspectRatio == null) {
            return inferredCanvas;
        }
        CanvasConfig canvas = new CanvasConfig();
        switch (aspectRatio) {
            case ASPECT_RATIO_PORTRAIT -> {
                canvas.setWidth(DEFAULT_PORTRAIT_WIDTH);
                canvas.setHeight(DEFAULT_PORTRAIT_HEIGHT);
            }
            case ASPECT_RATIO_LANDSCAPE -> {
                canvas.setWidth(DEFAULT_LANDSCAPE_WIDTH);
                canvas.setHeight(DEFAULT_LANDSCAPE_HEIGHT);
            }
            case ASPECT_RATIO_SQUARE -> {
                canvas.setWidth(DEFAULT_SQUARE_SIZE);
                canvas.setHeight(DEFAULT_SQUARE_SIZE);
            }
            case ASPECT_RATIO_FOUR_FIVE -> {
                canvas.setWidth(DEFAULT_FOUR_FIVE_WIDTH);
                canvas.setHeight(DEFAULT_FOUR_FIVE_HEIGHT);
            }
            default -> throw new BizException(ErrorCode.INVALID_REQUEST, "不支持的画面比例: " + aspectRatio);
        }
        int fps = inferredCanvas != null && inferredCanvas.getFps() != null && inferredCanvas.getFps() > 0
                ? inferredCanvas.getFps()
                : DEFAULT_CANVAS_FPS;
        canvas.setFps(fps);
        return canvas;
    }

    private String buildCanvasBrief(String aspectRatio, CanvasConfig targetCanvas) {
        if (targetCanvas == null) {
            return "当前未提供有效的画幅约束，请保持默认画幅。";
        }
        StringBuilder sb = new StringBuilder("目标画幅要求：\n");
        if (aspectRatio == null) {
            sb.append("- 当前未显式选择画面比例，请以系统推断的 canvas 为准。\n");
        } else {
            sb.append("- 用户已显式选择画面比例：").append(aspectRatio).append("。\n");
            sb.append("- 你必须严格按照该比例编排所有 scenes、文字排版、背景构图与媒体 fit，禁止擅自改成其他比例。\n");
        }
        sb.append("- 最终 canvas 必须满足：width=").append(targetCanvas.getWidth())
                .append("，height=").append(targetCanvas.getHeight())
                .append("，fps=").append(targetCanvas.getFps())
                .append("。\n");
        return sb.toString().trim();
    }

    private void applyCanvas(CompositionScript script, CanvasConfig targetCanvas) {
        if (script == null || targetCanvas == null) {
            return;
        }
        CanvasConfig canvas = script.getCanvas();
        if (canvas == null) {
            canvas = new CanvasConfig();
            script.setCanvas(canvas);
        }
        canvas.setWidth(targetCanvas.getWidth());
        canvas.setHeight(targetCanvas.getHeight());
        canvas.setFps(targetCanvas.getFps());
    }

    private void normalizeMediaSources(CompositionScript script) {
        if (script == null) {
            return;
        }
        if (script.getBgm() != null && script.getBgm().getSrc() != null && !script.getBgm().getSrc().isBlank()) {
            script.getBgm().setSrc(toRemotionMediaSrc(script.getBgm().getSrc()));
        }
        if (script.getScenes() == null) {
            return;
        }
        for (var scene : script.getScenes()) {
            if (scene == null || scene.getLayers() == null) {
                continue;
            }
            for (var layer : scene.getLayers()) {
                if (layer == null || layer.getParams() == null) {
                    continue;
                }
                Object srcValue = layer.getParams().get("src");
                if (srcValue instanceof String src && !src.isBlank()) {
                    layer.getParams().put("src", toRemotionMediaSrc(src));
                }
            }
        }
    }

    private String toRemotionMediaSrc(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        if (rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
            return rawPath;
        }
        Path localPath = resolveLocalMediaPath(rawPath);
        if (localPath == null) {
            return rawPath;
        }
        try {
            Path storageRoot = resolveStorageRoot();
            if (localPath.startsWith(storageRoot)) {
                String relative = storageRoot.relativize(localPath).toString().replace(File.separatorChar, '/');
                return trimTrailingSlash(remotionServiceUrl) + "/storage/" + relative;
            }
        } catch (Exception e) {
            log.warn("Failed to build remotion media src from path: {}", rawPath, e);
        }
        return rawPath;
    }

    private Path resolveLocalMediaPath(String rawPath) {
        try {
            if (rawPath.startsWith("file:///")) {
                return Paths.get(URI.create(rawPath)).toAbsolutePath().normalize();
            }
            Path asPath = Paths.get(rawPath);
            if (asPath.isAbsolute()) {
                return asPath.normalize();
            }
            return asPath.toAbsolutePath().normalize();
        } catch (Exception e) {
            log.warn("Failed to resolve local media path: {}", rawPath, e);
            return null;
        }
    }

    private Path resolveStorageRoot() {
        Path cwdStorage = Paths.get(STORAGE_DIR_NAME).toAbsolutePath().normalize();
        if (cwdStorage.toFile().exists()) {
            return cwdStorage;
        }
        Path parentStorage = Paths.get("..", STORAGE_DIR_NAME).toAbsolutePath().normalize();
        if (parentStorage.toFile().exists()) {
            return parentStorage;
        }
        return cwdStorage;
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:3001";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public RenderResponse getRenderStatus(String projectId) {
        // 先从 Redis 查询高频进度
        String redisKey = RENDER_TASK_KEY_PREFIX + projectId;
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        
        if (json != null) {
            try {
                RenderResponse resp = objectMapper.readValue(json, RenderResponse.class);
                // 状态同步
                if ("DONE".equals(resp.getStatus()) || "FAILED".equals(resp.getStatus())) {
                    CreationProjectEntity project = requireActiveProject(projectId);
                    if (!resp.getStatus().equals(project.getStatus())) {
                        project.setStatus(resp.getStatus());
                        this.baseMapper.updateById(project);
                    }
                }
                return resp;
            } catch (Exception e) {
                // 忽略解析错误，降级查数据库
            }
        }

        // 若 Redis 中无数据，查询 MySQL
        CreationProjectEntity project = requireActiveProject(projectId);
        RenderResponse response = new RenderResponse();
        response.setTaskId(projectId);
        
        if ("GENERATING".equals(project.getStatus())) {
            // Redis 里没数据，但 MySQL 是 GENERATING，说明可能刚提交或者意外丢失，暂时返回 QUEUED
            response.setStatus("QUEUED");
        } else if ("DONE".equals(project.getStatus())) {
            response.setStatus("DONE");
        } else {
            response.setStatus("NOT_STARTED");
        }
        
        return response;
    }

    private CompositionScript loadRetryableScript(String projectId, CreationProjectEntity project, CanvasConfig targetCanvas) {
        if (!"FAILED".equals(project.getStatus())) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(RENDER_SCRIPT_KEY_PREFIX + projectId);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            CompositionScript script = objectMapper.readValue(json, CompositionScript.class);
            if (!matchesCanvas(script, targetCanvas)) {
                log.info("Skip cached composition script because canvas changed: projectId={}, target={}x{}, cached={}x{}",
                        projectId,
                        targetCanvas == null ? null : targetCanvas.getWidth(),
                        targetCanvas == null ? null : targetCanvas.getHeight(),
                        script != null && script.getCanvas() != null ? script.getCanvas().getWidth() : null,
                        script != null && script.getCanvas() != null ? script.getCanvas().getHeight() : null);
                return null;
            }
            return script;
        } catch (Exception e) {
            log.warn("Failed to read cached composition script for retry: projectId={}", projectId, e);
            return null;
        }
    }

    private boolean matchesCanvas(CompositionScript script, CanvasConfig targetCanvas) {
        if (script == null || targetCanvas == null || script.getCanvas() == null) {
            return false;
        }
        return samePositive(script.getCanvas().getWidth(), targetCanvas.getWidth())
                && samePositive(script.getCanvas().getHeight(), targetCanvas.getHeight());
    }

    private boolean samePositive(Integer actual, Integer expected) {
        return actual != null && expected != null && actual > 0 && expected > 0 && actual.intValue() == expected.intValue();
    }

    private void persistOrchestrationArtifacts(String projectId, VideoOrchestrationResult orchestrationResult, CompositionScript script) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RENDER_SCRIPT_KEY_PREFIX + projectId,
                    objectMapper.writeValueAsString(script),
                    ORCHESTRATION_CACHE_TTL_DAYS,
                    TimeUnit.DAYS
            );

            Map<String, Object> meta = new HashMap<>();
            meta.put("projectId", projectId);
            meta.put("rawContent", orchestrationResult.getRawContent());
            meta.put("reasoningContent", orchestrationResult.getReasoningContent());
            meta.put("cachedAt", java.time.OffsetDateTime.now().toString());
            stringRedisTemplate.opsForValue().set(
                    RENDER_ORCHESTRATION_KEY_PREFIX + projectId,
                    objectMapper.writeValueAsString(meta),
                    ORCHESTRATION_CACHE_TTL_DAYS,
                    TimeUnit.DAYS
            );
        } catch (Exception e) {
            log.warn("Failed to persist orchestration artifacts: projectId={}", projectId, e);
        }
    }

    private void clearRenderCache(String projectId) {
        stringRedisTemplate.delete(RENDER_SCRIPT_KEY_PREFIX + projectId);
        stringRedisTemplate.delete(RENDER_ORCHESTRATION_KEY_PREFIX + projectId);
        stringRedisTemplate.delete(RENDER_TASK_KEY_PREFIX + projectId);
    }
}
