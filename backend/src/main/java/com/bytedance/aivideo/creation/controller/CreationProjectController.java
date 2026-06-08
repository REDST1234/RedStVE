package com.bytedance.aivideo.creation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.creation.dto.AdaptTriggerRequest;
import com.bytedance.aivideo.creation.dto.AdaptTriggerResponse;
import com.bytedance.aivideo.creation.dto.BindTemplateRequest;
import com.bytedance.aivideo.creation.dto.CompositionTimelineResponse;
import com.bytedance.aivideo.creation.dto.ConfirmAssetsResponse;
import com.bytedance.aivideo.creation.dto.CreateProjectRequest;
import com.bytedance.aivideo.creation.dto.CreateProjectResponse;
import com.bytedance.aivideo.creation.dto.CreativeAssetGridItemResponse;
import com.bytedance.aivideo.creation.dto.CreationProjectListItemResponse;
import com.bytedance.aivideo.creation.dto.CreationProjectListResponse;
import com.bytedance.aivideo.creation.dto.CreativeAssetItemResponse;
import com.bytedance.aivideo.creation.dto.GenerateVideoRequest;
import com.bytedance.aivideo.creation.dto.MatchResultItemResponse;
import com.bytedance.aivideo.creation.dto.MatchResultResponse;
import com.bytedance.aivideo.creation.dto.MatchTriggerRequest;
import com.bytedance.aivideo.creation.dto.MatchTriggerResponse;
import com.bytedance.aivideo.creation.dto.ProjectBgmBindingResponse;
import com.bytedance.aivideo.creation.dto.ProjectBgmSelectRequest;
import com.bytedance.aivideo.creation.dto.RenderFromJsonResponse;
import com.bytedance.aivideo.creation.dto.TimelineSegmentResponse;
import com.bytedance.aivideo.creation.dto.UpdateProjectRequest;
import com.bytedance.aivideo.creation.dto.UploadCreativeAssetResponse;
import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.entity.CreativeMaterialGridEntity;
import com.bytedance.aivideo.creation.entity.SlotMatchResultEntity;
import com.bytedance.aivideo.creation.mapper.CreativeMaterialGridMapper;
import com.bytedance.aivideo.creation.mapper.SlotMatchResultMapper;
import com.bytedance.aivideo.creation.service.AdaptationOrchestratorService;
import com.bytedance.aivideo.creation.service.CreationProjectService;
import com.bytedance.aivideo.creation.service.CreativeMaterialService;
import com.bytedance.aivideo.creation.service.SlotMatcherService;
import com.bytedance.aivideo.creation.service.TemplateRecommendService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/creation/projects")
public class CreationProjectController {

    private final CreationProjectService creationProjectService;
    private final CreativeMaterialService creativeMaterialService;
    private final SlotMatcherService slotMatcherService;
    private final AdaptationOrchestratorService adaptationOrchestratorService;
    private final SlotMatchResultMapper slotMatchResultMapper;
    private final CreativeMaterialGridMapper creativeMaterialGridMapper;
    private final TemplateRecommendService templateRecommendService;
    private final com.bytedance.aivideo.creation.service.BgmRecommendService bgmRecommendService;
    private final com.bytedance.aivideo.creation.service.CreationProjectBgmBindingService creationProjectBgmBindingService;

    public CreationProjectController(
            CreationProjectService creationProjectService,
            CreativeMaterialService creativeMaterialService,
            SlotMatcherService slotMatcherService,
            AdaptationOrchestratorService adaptationOrchestratorService,
            SlotMatchResultMapper slotMatchResultMapper,
            CreativeMaterialGridMapper creativeMaterialGridMapper,
            TemplateRecommendService templateRecommendService,
            com.bytedance.aivideo.creation.service.BgmRecommendService bgmRecommendService,
            com.bytedance.aivideo.creation.service.CreationProjectBgmBindingService creationProjectBgmBindingService
    ) {
        this.creationProjectService = creationProjectService;
        this.creativeMaterialService = creativeMaterialService;
        this.slotMatcherService = slotMatcherService;
        this.adaptationOrchestratorService = adaptationOrchestratorService;
        this.slotMatchResultMapper = slotMatchResultMapper;
        this.creativeMaterialGridMapper = creativeMaterialGridMapper;
        this.templateRecommendService = templateRecommendService;
        this.bgmRecommendService = bgmRecommendService;
        this.creationProjectBgmBindingService = creationProjectBgmBindingService;
    }

    @PostMapping
    public ApiResponse<CreateProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        CreationProjectEntity entity = creationProjectService.createProject(request.getTitle(), request.getDescription(), request.getAspectRatio());
        return ApiResponse.success(toCreateProjectResponse(entity));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<CreateProjectResponse> getProject(@PathVariable("projectId") String projectId) {
        CreationProjectEntity entity = creationProjectService.requireActiveProject(projectId);
        return ApiResponse.success(toCreateProjectResponse(entity));
    }

    @PutMapping("/{projectId}")
    public ApiResponse<CreateProjectResponse> updateProject(
            @PathVariable("projectId") String projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        CreationProjectEntity entity = creationProjectService.updateProjectBasics(projectId, request.getTitle(), request.getDescription(), request.getAspectRatio());
        return ApiResponse.success(toCreateProjectResponse(entity));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Boolean> deleteProject(@PathVariable("projectId") String projectId) {
        creationProjectService.deleteProject(projectId);
        return ApiResponse.success(Boolean.TRUE);
    }

    @GetMapping
    public ApiResponse<CreationProjectListResponse> listProjects(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        var resultPage = creationProjectService.listProjects(page, size, keyword);
        CreationProjectListResponse response = new CreationProjectListResponse();
        response.setPage(resultPage.getCurrent());
        response.setSize(resultPage.getSize());
        response.setTotal(resultPage.getTotal());
        response.setList(resultPage.getRecords().stream().map(this::toListItem).toList());
        return ApiResponse.success(response);
    }

    @PostMapping("/{projectId}/assets")
    public ApiResponse<UploadCreativeAssetResponse> uploadAsset(
            @PathVariable("projectId") String projectId,
            @RequestParam("materialType") String materialType,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "textContent", required = false) String textContent
    ) {
        CreativeMaterialEntity entity = creativeMaterialService.uploadAsset(projectId, materialType, file, textContent);
        return ApiResponse.success(toUploadResponse(entity));
    }

    @GetMapping("/{projectId}/assets")
    public ApiResponse<List<CreativeAssetItemResponse>> listAssets(@PathVariable("projectId") String projectId) {
        List<CreativeMaterialEntity> assets = creativeMaterialService.listProjectAssets(projectId);
        List<Long> materialBizIds = assets.stream()
                .map(CreativeMaterialEntity::getBizId)
                .toList();
        Map<Long, List<CreativeMaterialGridEntity>> gridMap = materialBizIds.isEmpty()
                ? Collections.emptyMap()
                : creativeMaterialGridMapper.selectList(
                        new LambdaQueryWrapper<CreativeMaterialGridEntity>()
                                .in(CreativeMaterialGridEntity::getMaterialBizId, materialBizIds)
                                .isNull(CreativeMaterialGridEntity::getDeletedAt)
                                .orderByAsc(CreativeMaterialGridEntity::getPageIndex)
                ).stream().collect(Collectors.groupingBy(CreativeMaterialGridEntity::getMaterialBizId));

        List<CreativeAssetItemResponse> items = assets.stream()
                .map(item -> toAssetItem(item, gridMap.getOrDefault(item.getBizId(), Collections.emptyList())))
                .toList();
        return ApiResponse.success(items);
    }

    @DeleteMapping("/{projectId}/assets/{materialBizId}")
    public ApiResponse<Boolean> deleteAsset(
            @PathVariable("projectId") String projectId,
            @PathVariable("materialBizId") String materialBizId
    ) {
        creativeMaterialService.deleteProjectAsset(projectId, materialBizId);
        return ApiResponse.success(Boolean.TRUE);
    }

    @PostMapping("/{projectId}/assets/confirm")
    public ApiResponse<ConfirmAssetsResponse> confirmAssets(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "type", required = false) String materialType
    ) {
        int totalAssets = creativeMaterialService.listProjectAssets(projectId).size();
        int triggered = creativeMaterialService.confirmAssetsForProfiling(projectId, materialType);
        ConfirmAssetsResponse response = new ConfirmAssetsResponse();
        response.setProjectId(projectId);
        response.setTotalAssets(totalAssets);
        response.setTriggeredCount(triggered);
        response.setStatus("PROFILING_TRIGGERED");
        return ApiResponse.success(response);
    }

    @PostMapping("/{projectId}/bind-template")
    public ApiResponse<CreateProjectResponse> bindTemplate(
            @PathVariable("projectId") String projectId,
            @Valid @RequestBody BindTemplateRequest request
    ) {
        CreationProjectEntity entity = creationProjectService.bindTemplate(projectId, request.getTemplateId(), request.getTemplateVersion());
        return ApiResponse.success(toCreateProjectResponse(entity));
    }

    @PostMapping("/{projectId}/recommend-templates")
    public ApiResponse<com.bytedance.aivideo.creation.dto.TemplateRecommendResponse> recommendTemplates(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) com.bytedance.aivideo.creation.dto.TemplateRecommendRequest request) {
        if (request == null) {
            request = new com.bytedance.aivideo.creation.dto.TemplateRecommendRequest();
        }
        boolean forceRefresh = request.getForceRefresh() != null && request.getForceRefresh();
        com.bytedance.aivideo.creation.dto.TemplateRecommendResponse response = templateRecommendService.recommend(
                projectId, request.getW1(), request.getW2(), request.getTopN(), forceRefresh);
        return ApiResponse.success(response);
    }

    @PostMapping("/{projectId}/recommend-bgm")
    public ApiResponse<com.bytedance.aivideo.creation.dto.BgmRecommendResponse> recommendBgm(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) com.bytedance.aivideo.creation.dto.BgmRecommendRequest request) {
        if (request == null) {
            request = new com.bytedance.aivideo.creation.dto.BgmRecommendRequest();
        }
        com.bytedance.aivideo.creation.dto.BgmRecommendResponse response = bgmRecommendService.recommend(
                projectId, request.getW1(), request.getW2(), request.getW3(), request.getTopN(),
                request.getForceRefresh() != null && request.getForceRefresh());
        return ApiResponse.success(response);
    }

    @PostMapping("/{projectId}/bgm/select")
    public ApiResponse<ProjectBgmBindingResponse> selectBgm(
            @PathVariable("projectId") String projectId,
            @RequestBody ProjectBgmSelectRequest request
    ) {
        return ApiResponse.success(creationProjectBgmBindingService.selectBgm(projectId, request));
    }

    @GetMapping("/{projectId}/bgm")
    public ApiResponse<ProjectBgmBindingResponse> getSelectedBgm(@PathVariable("projectId") String projectId) {
        return ApiResponse.success(creationProjectBgmBindingService.getCurrentBgm(projectId));
    }

    @DeleteMapping("/{projectId}/bgm")
    public ApiResponse<Boolean> clearSelectedBgm(@PathVariable("projectId") String projectId) {
        creationProjectBgmBindingService.clearCurrentBgm(projectId);
        return ApiResponse.success(Boolean.TRUE);
    }

    @PostMapping("/{projectId}/match")
    public ApiResponse<MatchTriggerResponse> triggerMatch(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) MatchTriggerRequest request
    ) {
        String versionId = slotMatcherService.matchSlots(projectId, request == null ? null : request.getVersionId());
        List<SlotMatchResultEntity> results = listMatchResults(projectId, versionId);
        MatchTriggerResponse response = new MatchTriggerResponse();
        response.setProjectId(projectId);
        response.setVersionId(versionId);
        response.setStatus("MATCHING");
        response.setMatchedSegmentCount((int) results.stream().filter(row -> "MATCHED".equals(row.getMatchStatus())).count());
        response.setMissingSegmentCount((int) results.stream()
                .filter(row -> "MISSING".equals(row.getMatchStatus()) || "VETOED".equals(row.getMatchStatus()))
                .count());
        return ApiResponse.success(response);
    }

    @GetMapping("/{projectId}/match-result")
    public ApiResponse<MatchResultResponse> getMatchResult(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "versionId", required = false) String versionId
    ) {
        String resolvedVersionId = resolveVersionId(projectId, versionId);
        List<SlotMatchResultEntity> rows = listMatchResults(projectId, resolvedVersionId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "匹配结果不存在: " + resolvedVersionId);
        }

        MatchResultResponse response = new MatchResultResponse();
        response.setProjectId(projectId);
        response.setVersionId(resolvedVersionId);
        response.setStatus("MATCHING");
        response.setOverallCoverage(calculateCoverage(rows));
        response.setItems(rows.stream().map(this::toMatchResultItem).toList());
        return ApiResponse.success(response);
    }

    @PostMapping("/{projectId}/adapt")
    public ApiResponse<AdaptTriggerResponse> triggerAdapt(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) AdaptTriggerRequest request
    ) {
        String versionId = adaptationOrchestratorService.orchestrateAdaptation(projectId, request == null ? null : request.getVersionId());
        List<SlotMatchResultEntity> rows = listMatchResults(projectId, versionId);
        AdaptTriggerResponse response = new AdaptTriggerResponse();
        response.setProjectId(projectId);
        response.setVersionId(versionId);
        response.setStatus("ADAPTING");
        response.setAdaptedCount((int) rows.stream().filter(item -> item.getAdaptedFilePath() != null && !item.getAdaptedFilePath().isBlank()).count());
        response.setFailedCount((int) rows.stream().filter(item -> item.getMatchedAssetId() == null || item.getMatchedAssetId().isBlank()).count());
        return ApiResponse.success(response);
    }

    @PostMapping("/{projectId}/segment/{segmentIndex}/regenerate-image")
    public ApiResponse<Boolean> regenerateImage(
            @PathVariable("projectId") String projectId,
            @PathVariable("segmentIndex") Integer segmentIndex,
            @RequestBody Map<String, String> payload
    ) {
        String prompt = payload.get("prompt");
        adaptationOrchestratorService.regenerateImageAsync(projectId, segmentIndex, prompt);
        return ApiResponse.success(Boolean.TRUE);
    }

    @GetMapping("/{projectId}/timeline")
    public ApiResponse<CompositionTimelineResponse> getTimeline(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "versionId", required = false) String versionId
    ) {
        String resolvedVersionId = resolveVersionId(projectId, versionId);
        List<SlotMatchResultEntity> rows = listMatchResults(projectId, resolvedVersionId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "未找到可展示的时间线: " + resolvedVersionId);
        }

        List<Long> bizIds = rows.stream()
                .map(SlotMatchResultEntity::getMatchedAssetId)
                .filter(id -> id != null && !id.isBlank())
                .map(Long::parseLong)
                .toList();
        Map<Long, CreativeMaterialEntity> materialMap = new java.util.HashMap<>();
        if (!bizIds.isEmpty()) {
            materialMap = creativeMaterialService.list(
                            new LambdaQueryWrapper<CreativeMaterialEntity>()
                                    .eq(CreativeMaterialEntity::getProjectId, projectId)
                                    .in(CreativeMaterialEntity::getBizId, bizIds)
                                    .isNull(CreativeMaterialEntity::getDeletedAt))
                    .stream()
                    .collect(Collectors.toMap(CreativeMaterialEntity::getBizId, item -> item, (a, b) -> a));
        }

        CompositionTimelineResponse response = new CompositionTimelineResponse();
        response.setProjectId(projectId);
        response.setVersionId(resolvedVersionId);
        response.setStatus("COMPOSED");
        List<TimelineSegmentResponse> segments = new ArrayList<>();
        for (SlotMatchResultEntity row : rows) {
            TimelineSegmentResponse segment = new TimelineSegmentResponse();
            segment.setSegmentIndex(row.getSegmentIndex());
            segment.setSegmentRole(row.getSegmentRole());
            segment.setMatchedAssetId(row.getMatchedAssetId());
            segment.setMatchStatus(row.getMatchStatus());
            segment.setAdaptedPath(row.getAdaptedFilePath());

            if (row.getMatchedAssetId() != null && !row.getMatchedAssetId().isBlank()) {
                CreativeMaterialEntity material = materialMap.get(Long.parseLong(row.getMatchedAssetId()));
                if (material != null) {
                    segment.setSourcePath(material.getFilePath());
                    segment.setDurationSeconds(material.getDuration());
                }
            }
            segments.add(segment);
        }
        response.setSegments(segments);
        return ApiResponse.success(response);
    }

    @PostMapping("/{projectId}/generate")
    public ApiResponse<Boolean> generateVideo(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) GenerateVideoRequest request
    ) {
        creationProjectService.generateVideo(projectId, request == null ? null : request.getAspectRatio());
        return ApiResponse.success(Boolean.TRUE);
    }

    @PostMapping("/{projectId}/regenerate")
    public ApiResponse<Boolean> regenerateVideo(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) GenerateVideoRequest request
    ) {
        creationProjectService.regenerateVideo(projectId, request == null ? null : request.getAspectRatio());
        return ApiResponse.success(Boolean.TRUE);
    }

    @GetMapping("/{projectId}/render-status")
    public ApiResponse<com.bytedance.aivideo.engine.remotion.dto.RenderResponse> getRenderStatus(@PathVariable("projectId") String projectId) {
        com.bytedance.aivideo.engine.remotion.dto.RenderResponse status = creationProjectService.getRenderStatus(projectId);
        return ApiResponse.success(status);
    }

    @GetMapping("/{projectId}/render-history")
    public ApiResponse<java.util.List<com.bytedance.aivideo.creation.entity.RenderRecordEntity>> getRenderHistory(@PathVariable("projectId") String projectId) {
        java.util.List<com.bytedance.aivideo.creation.entity.RenderRecordEntity> history = creationProjectService.getRenderHistory(projectId);
        return ApiResponse.success(history);
    }

    /**
     * [实验性] 直接提交 CompositionScript JSON 进行视频渲染。
     * 跳过 LLM 编排步骤，直接校验 → sanitize → 投递 Remotion。
     * 用于测试外部大模型输出的编排 JSON 效果。
     */
    @PostMapping("/render-from-json")
    public ApiResponse<RenderFromJsonResponse> renderFromJson(@RequestBody CompositionScript script) {
        RenderFromJsonResponse response = creationProjectService.renderFromJson(script);
        if ("FAILED".equals(response.getStatus())) {
            return ApiResponse.success(response); // 前端自行判断 error 字段
        }
        return ApiResponse.success(response);
    }

    /**
     * [实验性] 查询 JSON 直投渲染任务的状态。
     */
    @GetMapping("/render-from-json/{renderId}/status")
    public ApiResponse<com.bytedance.aivideo.engine.remotion.dto.RenderResponse> getJsonRenderStatus(
            @PathVariable("renderId") String renderId) {
        // renderFromJson 是 default 方法，需要通过 impl 实例调用
        com.bytedance.aivideo.creation.service.impl.CreationProjectServiceImpl impl =
                (com.bytedance.aivideo.creation.service.impl.CreationProjectServiceImpl) creationProjectService;
        com.bytedance.aivideo.engine.remotion.dto.RenderResponse status = impl.getJsonRenderStatus(renderId);
        return ApiResponse.success(status);
    }

    private List<SlotMatchResultEntity> listMatchResults(String projectId, String versionId) {
        return slotMatchResultMapper.selectList(
                new LambdaQueryWrapper<SlotMatchResultEntity>()
                        .eq(SlotMatchResultEntity::getProjectId, projectId)
                        .eq(SlotMatchResultEntity::getVersionId, versionId)
                        .isNull(SlotMatchResultEntity::getDeletedAt)
                        .orderByAsc(SlotMatchResultEntity::getSegmentIndex)
        );
    }

    private String resolveVersionId(String projectId, String versionId) {
        if (versionId != null && !versionId.isBlank()) {
            return versionId.trim();
        }
        SlotMatchResultEntity latest = slotMatchResultMapper.selectOne(
                new LambdaQueryWrapper<SlotMatchResultEntity>()
                        .eq(SlotMatchResultEntity::getProjectId, projectId)
                        .isNull(SlotMatchResultEntity::getDeletedAt)
                        .orderByDesc(SlotMatchResultEntity::getUpdatedAt)
                        .last("LIMIT 1")
        );
        if (latest == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "未找到匹配版本，请先触发 match");
        }
        return latest.getVersionId();
    }

    private double calculateCoverage(List<SlotMatchResultEntity> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        long covered = rows.stream().filter(row -> !"MISSING".equals(row.getMatchStatus())).count();
        return ((double) covered) / rows.size();
    }

    private CreateProjectResponse toCreateProjectResponse(CreationProjectEntity entity) {
        CreateProjectResponse response = new CreateProjectResponse();
        response.setProjectId(entity.getProjectId());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setStatus(entity.getStatus());
        response.setTemplateId(entity.getTemplateId());
        response.setTemplateSnapshotId(entity.getTemplateSnapshotId());
        response.setAspectRatio(entity.getRenderAspectRatio());
        response.setCreatedAt(toUtc(entity.getCreatedAt()));
        response.setUpdatedAt(toUtc(entity.getUpdatedAt()));
        return response;
    }

    private CreationProjectListItemResponse toListItem(CreationProjectEntity entity) {
        CreationProjectListItemResponse item = new CreationProjectListItemResponse();
        item.setProjectId(entity.getProjectId());
        item.setTitle(entity.getTitle());
        item.setDescription(entity.getDescription());
        item.setStatus(entity.getStatus());
        item.setTemplateId(entity.getTemplateId());
        item.setTemplateSnapshotId(entity.getTemplateSnapshotId());
        item.setCreatedAt(toUtc(entity.getCreatedAt()));
        item.setUpdatedAt(toUtc(entity.getUpdatedAt()));
        return item;
    }

    private UploadCreativeAssetResponse toUploadResponse(CreativeMaterialEntity entity) {
        UploadCreativeAssetResponse response = new UploadCreativeAssetResponse();
        response.setProjectId(entity.getProjectId());
        response.setMaterialBizId(String.valueOf(entity.getBizId()));
        response.setMaterialType(entity.getMaterialType());
        response.setOriginalFileName(entity.getOriginalFileName());
        response.setStatus(entity.getStatus());
        response.setFilePath(entity.getFilePath());
        response.setFileSize(entity.getFileSize());
        response.setCreatedAt(toUtc(entity.getCreatedAt()));
        return response;
    }

    private CreativeAssetItemResponse toAssetItem(CreativeMaterialEntity entity, List<CreativeMaterialGridEntity> gridRows) {
        CreativeAssetItemResponse item = new CreativeAssetItemResponse();
        item.setMaterialBizId(String.valueOf(entity.getBizId()));
        item.setMaterialType(entity.getMaterialType());
        item.setOriginalFileName(entity.getOriginalFileName());
        item.setStatus(entity.getStatus());
        item.setFilePath(entity.getFilePath());
        item.setFileSize(entity.getFileSize());
        item.setDuration(entity.getDuration());
        item.setWidth(entity.getWidth());
        item.setHeight(entity.getHeight());
        item.setFormat(entity.getFormat());
        item.setTextContent(entity.getTextContent());
        item.setProfileJson(entity.getProfileJson());
        item.setGridPages(gridRows.stream().map(this::toGridItem).toList());
        item.setCreatedAt(toUtc(entity.getCreatedAt()));
        item.setUpdatedAt(toUtc(entity.getUpdatedAt()));
        return item;
    }

    private CreativeAssetGridItemResponse toGridItem(CreativeMaterialGridEntity row) {
        CreativeAssetGridItemResponse item = new CreativeAssetGridItemResponse();
        item.setPageIndex(row.getPageIndex());
        item.setStatus(row.getStatus());
        item.setFilePath(row.getFilePath());
        item.setCacheKey("creation:asset:grid:" + row.getMaterialBizId() + ":" + row.getPageIndex());
        item.setLlmIncluded(Boolean.TRUE.equals(row.getLlmIncluded()));
        return item;
    }

    private MatchResultItemResponse toMatchResultItem(SlotMatchResultEntity row) {
        MatchResultItemResponse item = new MatchResultItemResponse();
        item.setSegmentIndex(row.getSegmentIndex());
        item.setSegmentRole(row.getSegmentRole());
        item.setMatchedAssetId(row.getMatchedAssetId());
        item.setMatchedHighlightId(row.getMatchedHighlightId());
        item.setMatchScore(row.getMatchScore());
        item.setMatchStatus(row.getMatchStatus());
        item.setMatchReason(row.getMatchReason());
        item.setVetoReason(row.getVetoReason());
        item.setAdaptationPlanJson(row.getAdaptationPlanJson());
        item.setAdaptedFilePath(row.getAdaptedFilePath());
        String url = toStorageUrl(row.getAdaptedFilePath());
        if (url != null && row.getUpdatedAt() != null) {
            url += "?t=" + row.getUpdatedAt().toEpochSecond(ZoneOffset.UTC);
        }
        item.setAdaptedFileUrl(url);
        item.setImageGenEligible(row.getImageGenEligible());
        item.setImageGenCategory(row.getImageGenCategory());
        item.setImageGenPrompt(row.getImageGenPrompt());
        item.setImageGenDescription(row.getImageGenDescription());
        item.setImageGenStatus(row.getImageGenStatus());
        item.setImageGenUrl(row.getImageGenUrl());
        item.setImageGenErrorMessage(row.getImageGenErrorMessage());
        return item;
    }

    /**
     * 将服务端绝对文件路径转为前端可访问的相对 URL。
     * d:\...\storage\creation-adapt\{projId}\{verId}\img_gen_seg_002.png
     * → /api/storage/creation-adapt/{projId}/{verId}/img_gen_seg_002.png
     */
    private String toStorageUrl(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return null;
        }
        String normalized = absolutePath.replace('\\', '/');
        int idx = normalized.lastIndexOf("/storage/");
        if (idx >= 0) {
            return "/api/storage/" + normalized.substring(idx + "/storage/".length());
        }
        return normalized;
    }

    private OffsetDateTime toUtc(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.ofHours(8)).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
