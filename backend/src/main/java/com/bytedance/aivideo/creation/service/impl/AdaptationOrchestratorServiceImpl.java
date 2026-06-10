package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.FfmpegCommandProperties;
import com.bytedance.aivideo.creation.entity.CreationFfmpegCommandLogEntity;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.entity.SlotMatchResultEntity;
import com.bytedance.aivideo.creation.event.ImageGenerationCompletedEvent;
import com.bytedance.aivideo.creation.mapper.CreationFfmpegCommandLogMapper;
import com.bytedance.aivideo.creation.mapper.SlotMatchResultMapper;
import com.bytedance.aivideo.creation.service.AdaptationOrchestratorService;
import com.bytedance.aivideo.creation.service.CreationProjectService;
import com.bytedance.aivideo.creation.service.CreativeMaterialService;
import com.bytedance.aivideo.engine.comfyui.ComfyuiBgRemovalService;
import com.bytedance.aivideo.engine.seedream.SeedreamImageService;
import com.bytedance.aivideo.engine.strategy.StrategyExecutor;
import com.bytedance.aivideo.engine.strategy.StrategyExecutionFragment;
import com.bytedance.aivideo.engine.strategy.StrategyOutputKind;
import com.bytedance.aivideo.engine.strategy.StrategyRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 素材适配编排服务：按 LLM adaptationPlan 组装并执行 FFmpeg 命令。
 */
@Slf4j
@Service
public class AdaptationOrchestratorServiceImpl implements AdaptationOrchestratorService {

    private static final String PROJECT_STATUS_ADAPTING = "ADAPTING";
    private static final String PROJECT_STATUS_COMPOSED = "COMPOSED";
    private static final String PROJECT_STATUS_FAILED = "FAILED";
    private static final String MATCH_STATUS_MATCHED = "MATCHED";
    private static final String MATCH_STATUS_PARTIAL = "PARTIAL";
    private static final String MATCH_STATUS_VETOED = "VETOED";
    private static final String MATCH_STATUS_MISSING = "MISSING";
    private static final String MATERIAL_TYPE_VIDEO = "VIDEO";
    private static final String MATERIAL_TYPE_IMAGE = "IMAGE";
    private static final Set<String> PROTECTED_VISUAL_KEYWORDS = Set.of(
            "logo",
            "brand_logo",
            "brandmark",
            "wordmark",
            "icon",
            "sticker",
            "badge",
            "illustration",
            "mascot",
            "挂件",
            "插图",
            "图标",
            "角标",
            "徽标",
            "徽章"
    );

    private final CreationProjectService creationProjectService;
    private final CreativeMaterialService creativeMaterialService;
    private final SlotMatchResultMapper slotMatchResultMapper;
    private final CreationFfmpegCommandLogMapper commandLogMapper;
    private final StrategyRouter strategyRouter;
    private final FfmpegCommandProperties ffmpegCommandProperties;
    private final SeedreamImageService seedreamImageService;
    private final ComfyuiBgRemovalService comfyuiBgRemovalService;
    private final com.bytedance.aivideo.engine.ffmpeg.api.MediaProbeEngine ffprobeEngine;
    private final ObjectMapper objectMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public AdaptationOrchestratorServiceImpl(
            CreationProjectService creationProjectService,
            CreativeMaterialService creativeMaterialService,
            SlotMatchResultMapper slotMatchResultMapper,
            CreationFfmpegCommandLogMapper commandLogMapper,
            StrategyRouter strategyRouter,
            FfmpegCommandProperties ffmpegCommandProperties,
            SeedreamImageService seedreamImageService,
            ComfyuiBgRemovalService comfyuiBgRemovalService,
            com.bytedance.aivideo.engine.ffmpeg.api.MediaProbeEngine ffprobeEngine,
            ObjectMapper objectMapper,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.creationProjectService = creationProjectService;
        this.creativeMaterialService = creativeMaterialService;
        this.slotMatchResultMapper = slotMatchResultMapper;
        this.commandLogMapper = commandLogMapper;
        this.strategyRouter = strategyRouter;
        this.ffmpegCommandProperties = ffmpegCommandProperties;
        this.seedreamImageService = seedreamImageService;
        this.comfyuiBgRemovalService = comfyuiBgRemovalService;
        this.ffprobeEngine = ffprobeEngine;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String orchestrateAdaptation(String projectId, String versionId) {
        String resolvedVersionId = resolveVersionId(projectId, versionId);
        CreationProjectEntity project = creationProjectService.requireActiveProject(projectId);
        project.setStatus(PROJECT_STATUS_ADAPTING);
        creationProjectService.updateById(project);

        eventPublisher.publishEvent(new com.bytedance.aivideo.creation.event.AdaptationTriggeredEvent(this, projectId, resolvedVersionId));
        return resolvedVersionId;
    }

    @Async("videoTaskExecutor")
    @EventListener
    public void handleAdaptationTriggered(com.bytedance.aivideo.creation.event.AdaptationTriggeredEvent event) {
        String projectId = event.getProjectId();
        String resolvedVersionId = event.getVersionId();
        long startedAt = System.currentTimeMillis();

        CreationProjectEntity project = creationProjectService.requireActiveProject(projectId);
        List<SlotMatchResultEntity> matches = listMatchResults(projectId, resolvedVersionId);
        if (matches.isEmpty()) {
            log.warn("未找到可适配的匹配结果: {}", resolvedVersionId);
            project.setStatus(PROJECT_STATUS_FAILED);
            creationProjectService.updateById(project);
            return;
        }

        int failedCount = 0;
        for (SlotMatchResultEntity item : matches) {
            long rowStartedAt = System.currentTimeMillis();
            try {
                if (isSkippedMatch(item)) {
                    writeVetoLog(projectId, resolvedVersionId, item);
                    failedCount++;
                    continue;
                }
                // Handle MISSING or PARTIAL slots eligible for Seedream image generation
                if ((MATCH_STATUS_MISSING.equals(item.getMatchStatus()) || MATCH_STATUS_PARTIAL.equals(item.getMatchStatus()))
                        && Boolean.TRUE.equals(item.getImageGenEligible())) {
                    handleImageGeneration(projectId, resolvedVersionId, item);
                    if ("FAILED".equals(item.getImageGenStatus())) {
                        failedCount++;
                    }
                    continue;
                }
                if (item.getAdaptedFilePath() != null && !item.getAdaptedFilePath().isBlank()) {
                    continue;
                }
                CreativeMaterialEntity material = findMaterial(projectId, item.getMatchedAssetId());
                if (material == null || material.getFilePath() == null || material.getFilePath().isBlank()) {
                    writeFailureLog(projectId, resolvedVersionId, item, null, "MATERIAL_UNAVAILABLE", "素材不存在或文件路径为空");
                    failedCount++;
                    continue;
                }
                String outputPath = applyStrategyChain(projectId, resolvedVersionId, item, material);
                item.setAdaptedFilePath(outputPath);

                try {
                    com.bytedance.aivideo.engine.ffmpeg.model.MediaProbeResult probeResult = ffprobeEngine.probe(Paths.get(outputPath));
                    if (probeResult != null && probeResult.getDuration() != null) {
                        String currentPlan = item.getAdaptationPlanJson();
                        JsonNode root = objectMapper.readTree(currentPlan == null || currentPlan.isBlank() ? "{}" : currentPlan);
                        if (root.isObject()) {
                            ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("actualDuration", probeResult.getDuration());
                            item.setAdaptationPlanJson(root.toString());
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Failed to probe adapted file duration for item {}", item.getMatchId(), ex);
                }

                slotMatchResultMapper.updateById(item);
                log.info("adapt row finished: projectId={}, versionId={}, segmentIndex={}, outputPath={}, elapsedMs={}",
                        projectId, resolvedVersionId, item.getSegmentIndex(), outputPath, System.currentTimeMillis() - rowStartedAt);
            } catch (Exception ex) {
                failedCount++;
                log.warn("adapt row failed: projectId={}, versionId={}, segmentIndex={}, reason={}",
                        projectId, resolvedVersionId, item.getSegmentIndex(), ex.getMessage());
            }
        }

        project.setStatus(failedCount >= matches.size() ? PROJECT_STATUS_FAILED : PROJECT_STATUS_COMPOSED);
        creationProjectService.updateById(project);
        log.info("adaptation finished: projectId={}, versionId={}, total={}, failed={}, elapsedMs={}",
                projectId, resolvedVersionId, matches.size(), failedCount, System.currentTimeMillis() - startedAt);
    }

    @Override
    @Async("videoTaskExecutor")
    public void regenerateImageAsync(String projectId, int segmentIndex, String newPrompt) {
        CreationProjectEntity project = creationProjectService.getOne(
                new LambdaQueryWrapper<CreationProjectEntity>().eq(CreationProjectEntity::getProjectId, projectId)
        );
        if (project == null) {
            log.warn("regenerateImageAsync failed: project not found {}", projectId);
            return;
        }

        SlotMatchResultEntity latest = slotMatchResultMapper.selectOne(
                new LambdaQueryWrapper<SlotMatchResultEntity>()
                        .eq(SlotMatchResultEntity::getProjectId, projectId)
                        .isNull(SlotMatchResultEntity::getDeletedAt)
                        .orderByDesc(SlotMatchResultEntity::getUpdatedAt)
                        .last("LIMIT 1")
        );
        if (latest == null || latest.getVersionId() == null) {
            log.warn("regenerateImageAsync failed: no matched version for {}", projectId);
            return;
        }
        String versionId = latest.getVersionId();

        SlotMatchResultEntity item = slotMatchResultMapper.selectOne(
                new LambdaQueryWrapper<SlotMatchResultEntity>()
                        .eq(SlotMatchResultEntity::getProjectId, projectId)
                        .eq(SlotMatchResultEntity::getVersionId, versionId)
                        .eq(SlotMatchResultEntity::getSegmentIndex, segmentIndex)
                        .isNull(SlotMatchResultEntity::getDeletedAt)
                        .last("LIMIT 1")
        );

        if (item == null) {
            log.warn("regenerateImageAsync failed: item not found for project={}, segment={}", projectId, segmentIndex);
            return;
        }

        if (newPrompt != null && !newPrompt.isBlank()) {
            item.setImageGenPrompt(newPrompt.trim());
        }
        item.setImageGenStatus("PENDING");
        item.setImageGenUrl(null);
        item.setAdaptedFilePath(null);
        slotMatchResultMapper.updateById(item);

        log.info("Starting async regeneration for projectId={}, segmentIndex={}", projectId, segmentIndex);
        handleImageGeneration(projectId, versionId, item);
    }

    private String applyStrategyChain(
            String projectId,
            String versionId,
            SlotMatchResultEntity row,
            CreativeMaterialEntity material
    ) throws IOException {
        JsonNode strategyChain = parseStrategyChain(row.getAdaptationPlanJson());
        Path sourcePath = com.bytedance.aivideo.creation.util.StoragePathResolver.resolveToCurrentAbsolutePath(material.getFilePath());
        Path outputDir = resolveStorageRoot().resolve("creation-adapt").resolve(projectId).resolve(versionId);
        Files.createDirectories(outputDir);

        if (isProtectedVisualAsset(material)) {
            log.info("skip ffmpeg adaptation for protected visual asset: projectId={}, versionId={}, segmentIndex={}, materialBizId={}",
                    projectId, versionId, row.getSegmentIndex(), material.getBizId());
            return copySourceAsset(sourcePath, outputDir, row, material);
        }

        if (!strategyChain.isArray() || strategyChain.isEmpty()) {
            return copySourceAsset(sourcePath, outputDir, row, material);
        }

        Path currentInput = sourcePath;
        Path finalOutput = currentInput;
        int stepIndex = 0;
        boolean currentHasAudio = detectHasAudio(material);
        for (int index = 0; index < strategyChain.size(); index++) {
            JsonNode strategy = strategyChain.get(index);
            StrategyBuildResult buildResult = buildStrategyFragment(strategy, material, currentHasAudio);
            if (buildResult == null) {
                continue;
            }

            StrategyExecutionFragment fragment = buildResult.fragment();
            ensureUsableFragment(projectId, versionId, row, material, buildResult.strategyType(), fragment);

            List<StrategyBuildResult> aggregatedResults = new ArrayList<>();
            if (isVideoFilterChainEligible(fragment, currentInput)) {
                aggregatedResults.add(buildResult);
                int cursor = index + 1;
                while (cursor < strategyChain.size()) {
                    StrategyBuildResult next = buildStrategyFragment(strategyChain.get(cursor), material, currentHasAudio);
                    if (next == null || !isVideoFilterChainEligible(next.fragment(), currentInput)) {
                        break;
                    }
                    ensureUsableFragment(projectId, versionId, row, material, next.strategyType(), next.fragment());
                    aggregatedResults.add(next);
                    cursor++;
                }
                index = cursor - 1;
            } else {
                aggregatedResults.add(buildResult);
            }

            stepIndex++;
            String stepName = aggregatedResults.stream()
                    .map(StrategyBuildResult::strategyType)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .reduce((a, b) -> a + "_then_" + b)
                    .orElse("step");
            StrategyExecutionFragment lastFragment = aggregatedResults.getLast().fragment();
            Path stepOutput = buildStepOutput(outputDir, row.getSegmentIndex(), stepIndex, stepName, lastFragment, currentInput, material);
            List<String> command = aggregatedResults.size() == 1 && !isVideoFilterChainEligible(fragment, currentInput)
                    ? buildStandaloneCommand(currentInput, stepOutput, aggregatedResults.getFirst().fragment(), currentHasAudio)
                    : buildAggregatedVideoCommand(currentInput, stepOutput, aggregatedResults, currentHasAudio);
            CreationFfmpegCommandLogEntity logRow = insertRunningLog(projectId, versionId, row, material, stepName.toUpperCase(Locale.ROOT), command);
            CommandExecutionResult result = executeCommand(command);
            finishCommandLog(logRow, result);
            if (result.exitCode() != 0) {
                throw new BizException(ErrorCode.FFMPEG_ERROR, "FFmpeg执行失败: " + result.stderrTail());
            }
            finalOutput = stepOutput;
            currentInput = stepOutput;
            currentHasAudio = detectOutputHasAudio(currentHasAudio, lastFragment);
        }
        return finalOutput.toString();
    }

    private StrategyBuildResult buildStrategyFragment(JsonNode strategy, CreativeMaterialEntity material, boolean hasAudio) {
        String strategyType = strategy.path("strategyType").asText("").trim().toUpperCase(Locale.ROOT);
        if (strategyType.isBlank()) {
            return null;
        }
        StrategyExecutor executor = strategyRouter.getExecutor(strategyType);
        String inputContext = buildStrategyInputContext(strategy.path("params"), material);
        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, hasAudio);
        return new StrategyBuildResult(strategyType, fragment);
    }

    private String buildStrategyInputContext(JsonNode paramsNode, CreativeMaterialEntity material) {
        ObjectNode contextNode = paramsNode != null && paramsNode.isObject()
                ? paramsNode.deepCopy()
                : objectMapper.createObjectNode();
        contextNode.put("materialType", material.getMaterialType());
        if (material.getWidth() != null) {
            contextNode.put("sourceWidth", material.getWidth());
        }
        if (material.getHeight() != null) {
            contextNode.put("sourceHeight", material.getHeight());
        }
        if (material.getFormat() != null) {
            contextNode.put("sourceFormat", material.getFormat());
        }
        return contextNode.toString();
    }

    private void ensureUsableFragment(
            String projectId,
            String versionId,
            SlotMatchResultEntity row,
            CreativeMaterialEntity material,
            String strategyType,
            StrategyExecutionFragment fragment
    ) {
        boolean usable = fragment.hasExplicitFilterComplex()
                || fragment.hasSimpleFilters()
                || !fragment.getPreInputArgs().isEmpty()
                || !fragment.getExtraArgs().isEmpty()
                || fragment.isLoopImageInput();
        if (usable) {
            return;
        }
        writeFailureLog(projectId, versionId, row, material, strategyType, "策略未生成任何可执行 FFmpeg 片段");
        throw new BizException(ErrorCode.INVALID_REQUEST, "策略未生成任何可执行 FFmpeg 片段: " + strategyType);
    }

    private boolean isVideoFilterChainEligible(StrategyExecutionFragment fragment, Path currentInput) {
        return fragment.getOutputKind() == StrategyOutputKind.VIDEO_OUTPUT
                && !fragment.isLoopImageInput()
                && !fragment.hasExplicitFilterComplex()
                && fragment.getMapArgs().isEmpty()
                && fragment.getFilterComplex() == null
                && !isImagePath(currentInput);
    }

    private List<String> buildAggregatedVideoCommand(
            Path input,
            Path output,
            List<StrategyBuildResult> fragments,
            boolean hasAudio
    ) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegCommandProperties.getPath());
        command.add("-hide_banner");
        command.add("-y");
        List<String> preInputArgs = new ArrayList<>();
        List<String> videoFilters = new ArrayList<>();
        List<String> audioFilters = new ArrayList<>();
        for (StrategyBuildResult item : fragments) {
            preInputArgs.addAll(item.fragment().getPreInputArgs());
            videoFilters.addAll(item.fragment().getVideoFilters());
            if (hasAudio) {
                audioFilters.addAll(item.fragment().getAudioFilters());
            }
        }
        command.addAll(preInputArgs);
        command.add("-i");
        command.add(input.toString());
        if (!audioFilters.isEmpty()) {
            command.add("-filter_complex");
            command.add(buildVideoAudioFilterComplex(videoFilters, audioFilters));
            command.add("-map");
            command.add("[outv]");
            command.add("-map");
            command.add("[outa]");
        } else if (!videoFilters.isEmpty()) {
            command.add("-vf");
            command.add(String.join(",", videoFilters));
            if (!hasAudio) {
                command.add("-an");
            }
        } else if (!hasAudio) {
            command.add("-an");
        }
        command.add(output.toString());
        return command;
    }

    private List<String> buildStandaloneCommand(
            Path input,
            Path output,
            StrategyExecutionFragment fragment,
            boolean hasAudio
    ) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegCommandProperties.getPath());
        command.add("-hide_banner");
        command.add("-y");
        if (fragment.isLoopImageInput() && isImagePath(input)) {
            command.add("-loop");
            command.add("1");
        }
        command.addAll(fragment.getPreInputArgs());
        command.add("-i");
        command.add(input.toString());
        if (fragment.hasExplicitFilterComplex()) {
            command.add("-filter_complex");
            command.add(fragment.getFilterComplex());
            command.addAll(fragment.getMapArgs());
        } else if (!fragment.getAudioFilters().isEmpty() && hasAudio) {
            command.add("-filter_complex");
            command.add(buildVideoAudioFilterComplex(fragment.getVideoFilters(), fragment.getAudioFilters()));
            command.add("-map");
            command.add("[outv]");
            command.add("-map");
            command.add("[outa]");
        } else if (!fragment.getVideoFilters().isEmpty()) {
            command.add("-vf");
            command.add(String.join(",", fragment.getVideoFilters()));
            if (!hasAudio || !fragment.isPreserveAudio()) {
                command.add("-an");
            }
        } else if (!hasAudio || !fragment.isPreserveAudio()) {
            command.add("-an");
        }
        command.addAll(fragment.getExtraArgs());
        command.add(output.toString());
        return command;
    }

    private String buildVideoAudioFilterComplex(List<String> videoFilters, List<String> audioFilters) {
        String videoChain = videoFilters.isEmpty() ? "null" : String.join(",", videoFilters);
        String audioChain = audioFilters.isEmpty() ? "anull" : String.join(",", audioFilters);
        return String.format("[0:v]%s[outv];[0:a]%s[outa]", videoChain, audioChain);
    }

    private Path buildStepOutput(
            Path outputDir,
            int segmentIndex,
            int stepIndex,
            String stepName,
            StrategyExecutionFragment fragment,
            Path currentInput,
            CreativeMaterialEntity material
    ) {
        String extension;
        if (fragment.getOutputKind() == StrategyOutputKind.IMAGE_OUTPUT) {
            extension = normalizeImageExtension(fragment.getPreferredExtension(), currentInput, material.getFormat());
        } else {
            extension = "mp4";
        }
        return outputDir.resolve(String.format(
                "seg_%03d_step_%02d_%s.%s",
                segmentIndex,
                stepIndex,
                stepName,
                extension
        ));
    }

    private CommandExecutionResult executeCommand(List<String> command) {
        long startedAt = System.currentTimeMillis();
        Path stdoutFile = null;
        Path stderrFile = null;
        try {
            stdoutFile = Files.createTempFile("creation-ffmpeg-stdout-", ".log");
            stderrFile = Files.createTempFile("creation-ffmpeg-stderr-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectOutput(stdoutFile.toFile());
            builder.redirectError(stderrFile.toFile());
            Process process = builder.start();
            boolean finished = process.waitFor(ffmpegCommandProperties.getOperationTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandExecutionResult(-1, System.currentTimeMillis() - startedAt,
                        readTail(stdoutFile), appendMessage(readTail(stderrFile), "FFmpeg command timeout"));
            }
            return new CommandExecutionResult(process.exitValue(), System.currentTimeMillis() - startedAt,
                    readTail(stdoutFile), readTail(stderrFile));
        } catch (Exception ex) {
            return new CommandExecutionResult(-1, System.currentTimeMillis() - startedAt, "", ex.getMessage());
        } finally {
            deleteQuietly(stdoutFile);
            deleteQuietly(stderrFile);
        }
    }

    private CreationFfmpegCommandLogEntity insertRunningLog(
            String projectId,
            String versionId,
            SlotMatchResultEntity row,
            CreativeMaterialEntity material,
            String strategyType,
            List<String> command
    ) {
        CreationFfmpegCommandLogEntity entity = new CreationFfmpegCommandLogEntity();
        entity.setProjectId(projectId);
        entity.setVersionId(versionId);
        entity.setMatchId(row.getMatchId());
        entity.setSegmentIndex(row.getSegmentIndex());
        entity.setMaterialBizId(material == null ? null : String.valueOf(material.getBizId()));
        entity.setStrategyType(strategyType);
        entity.setCommandText(toCommandText(command));
        entity.setStatus("RUNNING");
        commandLogMapper.insert(entity);
        log.info("ffmpeg command running: projectId={}, versionId={}, segmentIndex={}, strategyType={}, command={}",
                projectId, versionId, row.getSegmentIndex(), strategyType, entity.getCommandText());
        return entity;
    }

    private void finishCommandLog(CreationFfmpegCommandLogEntity entity, CommandExecutionResult result) {
        entity.setExitCode(result.exitCode());
        entity.setElapsedMs(result.elapsedMs());
        entity.setStdoutTail(result.stdoutTail());
        entity.setStderrTail(result.stderrTail());
        entity.setStatus(result.exitCode() == 0 ? "SUCCESS" : "FAILED");
        if (result.exitCode() != 0) {
            entity.setErrorMessage(result.stderrTail());
        }
        commandLogMapper.updateById(entity);
        log.info("ffmpeg command finished: logBizId={}, status={}, exitCode={}, elapsedMs={}, stderrTail={}",
                entity.getBizId(), entity.getStatus(), entity.getExitCode(), entity.getElapsedMs(), entity.getStderrTail());
    }

    private void writeVetoLog(String projectId, String versionId, SlotMatchResultEntity row) {
        CreationFfmpegCommandLogEntity entity = new CreationFfmpegCommandLogEntity();
        entity.setProjectId(projectId);
        entity.setVersionId(versionId);
        entity.setMatchId(row.getMatchId());
        entity.setSegmentIndex(row.getSegmentIndex());
        entity.setMaterialBizId(row.getMatchedAssetId());
        entity.setStrategyType(row.getMatchStatus());
        entity.setStatus("VETOED");
        entity.setVetoReason(firstNonBlank(row.getVetoReason(), row.getMatchReason(), "槽位不可执行"));
        commandLogMapper.insert(entity);
    }

    private void writeFailureLog(String projectId, String versionId, SlotMatchResultEntity row, CreativeMaterialEntity material, String strategyType, String reason) {
        CreationFfmpegCommandLogEntity entity = new CreationFfmpegCommandLogEntity();
        entity.setProjectId(projectId);
        entity.setVersionId(versionId);
        entity.setMatchId(row.getMatchId());
        entity.setSegmentIndex(row.getSegmentIndex());
        entity.setMaterialBizId(material == null ? row.getMatchedAssetId() : String.valueOf(material.getBizId()));
        entity.setStrategyType(strategyType);
        entity.setStatus("FAILED");
        entity.setErrorMessage(reason);
        commandLogMapper.insert(entity);
    }

    private JsonNode parseStrategyChain(String adaptationPlanJson) {
        try {
            if (adaptationPlanJson == null || adaptationPlanJson.isBlank()) {
                return objectMapper.createArrayNode();
            }
            JsonNode root = objectMapper.readTree(adaptationPlanJson);
            JsonNode chain = root.path("strategyChain");
            return chain.isArray() ? chain : objectMapper.createArrayNode();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "adaptationPlanJson 非法: " + ex.getMessage());
        }
    }

    private boolean isSkippedMatch(SlotMatchResultEntity item) {
        // MISSING slots eligible for image generation are handled separately, not skipped
        if ((MATCH_STATUS_MISSING.equals(item.getMatchStatus()) || MATCH_STATUS_PARTIAL.equals(item.getMatchStatus()))
                && Boolean.TRUE.equals(item.getImageGenEligible())) {
            return false;
        }
        return MATCH_STATUS_MISSING.equals(item.getMatchStatus())
                || MATCH_STATUS_VETOED.equals(item.getMatchStatus())
                || item.getMatchedAssetId() == null
                || item.getMatchedAssetId().isBlank()
                || !(MATCH_STATUS_MATCHED.equals(item.getMatchStatus()) || MATCH_STATUS_PARTIAL.equals(item.getMatchStatus()));
    }

    private CreativeMaterialEntity findMaterial(String projectId, String materialBizId) {
        return creativeMaterialService.lambdaQuery()
                .eq(CreativeMaterialEntity::getProjectId, projectId)
                .eq(CreativeMaterialEntity::getBizId, Long.parseLong(materialBizId))
                .isNull(CreativeMaterialEntity::getDeletedAt)
                .last("LIMIT 1")
                .one();
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

    private List<SlotMatchResultEntity> listMatchResults(String projectId, String versionId) {
        return slotMatchResultMapper.selectList(
                new LambdaQueryWrapper<SlotMatchResultEntity>()
                        .eq(SlotMatchResultEntity::getProjectId, projectId)
                        .eq(SlotMatchResultEntity::getVersionId, versionId)
                        .isNull(SlotMatchResultEntity::getDeletedAt)
                        .orderByAsc(SlotMatchResultEntity::getSegmentIndex)
        );
    }

    private String safeExtension(CreativeMaterialEntity material) {
        String format = material.getFormat();
        return format == null || format.isBlank() ? "bin" : format.trim();
    }

    private String extensionFromPathOrFormat(Path path, String format) {
        if (path != null) {
            String fileName = path.getFileName().toString();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
                return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
            }
        }
        return safeExtensionValue(format, "bin");
    }

    private String normalizeImageExtension(String preferredExtension, Path currentInput, String format) {
        String extension = safeExtensionValue(preferredExtension, null);
        if (extension == null && currentInput != null && isImagePath(currentInput)) {
            extension = extensionFromPathOrFormat(currentInput, format);
        }
        if (extension == null) {
            extension = safeExtensionValue(format, "jpg");
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg", "png", "webp" -> extension.toLowerCase(Locale.ROOT);
            default -> "jpg";
        };
    }

    private String safeExtensionValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private boolean isImagePath(Path path) {
        String value = path == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return value.endsWith(".jpg") || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".webp");
    }

    private boolean detectHasAudio(CreativeMaterialEntity material) {
        if (!MATERIAL_TYPE_VIDEO.equalsIgnoreCase(material.getMaterialType())) {
            return false;
        }
        String profileJson = material.getProfileJson();
        if (profileJson == null || profileJson.isBlank()) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(profileJson);
            JsonNode hasAudioNode = root.path("physicalAttributes").path("hasAudio");
            return !hasAudioNode.isBoolean() || hasAudioNode.asBoolean();
        } catch (Exception ex) {
            return true;
        }
    }

    private boolean detectOutputHasAudio(boolean currentHasAudio, StrategyExecutionFragment fragment) {
        if (fragment.getOutputKind() == StrategyOutputKind.IMAGE_OUTPUT) {
            return false;
        }
        return currentHasAudio && fragment.isPreserveAudio();
    }

    private String copySourceAsset(Path sourcePath, Path outputDir, SlotMatchResultEntity row, CreativeMaterialEntity material) throws IOException {
        Path copied = outputDir.resolve("seg_" + row.getSegmentIndex() + "_" + row.getSegmentRole() + "_source." + extensionFromPathOrFormat(sourcePath, material.getFormat()));
        Files.copy(sourcePath, copied, StandardCopyOption.REPLACE_EXISTING);
        return copied.toString();
    }

    private boolean isProtectedVisualAsset(CreativeMaterialEntity material) {
        if (material == null || !MATERIAL_TYPE_IMAGE.equalsIgnoreCase(material.getMaterialType())) {
            return false;
        }
        if (containsProtectedKeyword(material.getOriginalFileName())
                || containsProtectedKeyword(material.getDescription())
                || containsProtectedKeyword(material.getTags())
                || containsProtectedKeyword(material.getTextContent())) {
            return true;
        }
        String profileJson = material.getProfileJson();
        if (profileJson == null || profileJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(profileJson);
            if (nodeContainsProtectedKeyword(root.path("semanticTags").path("mainEntities"))
                    || nodeContainsProtectedKeyword(root.path("semanticTags").path("mainKeywords"))
                    || nodeContainsProtectedKeyword(root.path("semanticTags").path("overallStyle"))
                    || nodeContainsProtectedKeyword(root.path("highlights"))
                    || nodeContainsProtectedKeyword(root.path("semanticTags"))) {
                return true;
            }
        } catch (Exception ex) {
            return containsProtectedKeyword(profileJson);
        }
        return containsProtectedKeyword(profileJson);
    }

    private boolean nodeContainsProtectedKeyword(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return containsProtectedKeyword(node.asText(""));
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (nodeContainsProtectedKeyword(child)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (containsProtectedKeyword(entry.getKey()) || nodeContainsProtectedKeyword(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsProtectedKeyword(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (String keyword : PROTECTED_VISUAL_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String toCommandText(List<String> command) {
        return command.stream()
                .map(this::quoteIfNeeded)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private String quoteIfNeeded(String arg) {
        if (arg == null) {
            return "";
        }
        if (arg.contains(" ") || arg.contains("\t")) {
            return "\"" + arg.replace("\"", "\\\"") + "\"";
        }
        return arg;
    }

    private String readTail(Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            int max = 4000;
            return content.length() <= max ? content : content.substring(content.length() - max);
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    private void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
        }
    }

    private String appendMessage(String original, String message) {
        if (original == null || original.isBlank()) {
            return message;
        }
        return original + "\n" + message;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void handleImageGeneration(
            String projectId,
            String versionId,
            SlotMatchResultEntity item
    ) {
        if ("COMPLETED".equals(item.getImageGenStatus())) {
            return;
        }

        item.setImageGenStatus("PROCESSING");
        slotMatchResultMapper.updateById(item);
        writeImageGenLog(projectId, versionId, item, "PROCESSING", null, null);

        String prompt = item.getImageGenPrompt();
        String category = item.getImageGenCategory();
        prompt = enforceTransparencyPrompt(prompt, category);
        String negativePrompt = transparencyNegativePrompt(category);

        long startedAt = System.currentTimeMillis();
        try {
            String imageUrl = seedreamImageService.generateImageUrl(prompt, negativePrompt);

            // Download and persist locally — Seedream URLs may expire
            String localPath = downloadGeneratedImage(projectId, versionId, item.getSegmentIndex(), imageUrl);

            // ComfyUI background removal for categories that need transparent backgrounds
            if (Set.of("UI_ELEMENT", "STICKER", "LOGO").contains(category)) {
                try {
                    Path processedPath = comfyuiBgRemovalService.removeBackground(Paths.get(localPath));
                    localPath = processedPath.toString();
                    log.info("comfyui bg removal completed: localPath={}", localPath);
                } catch (Exception ex) {
                    log.warn("comfyui bg removal failed, keeping original image: {}", ex.getMessage());
                    // 非致命：保留原图继续，不阻断流程
                }
            }

            item.setImageGenUrl(imageUrl);
            item.setImageGenStatus("COMPLETED");
            item.setAdaptedFilePath(localPath);
            item.setImageGenErrorMessage(null);
            slotMatchResultMapper.updateById(item);

            writeImageGenLog(projectId, versionId, item, "COMPLETED", localPath, null);

            eventPublisher.publishEvent(new ImageGenerationCompletedEvent(
                    this, projectId, versionId, item.getMatchId(), item.getSegmentIndex(),
                    true, localPath, null
            ));

            log.info("image generation completed: projectId={}, segmentIndex={}, category={}, localPath={}, url={}, elapsedMs={}",
                    projectId, item.getSegmentIndex(), category, localPath, imageUrl, System.currentTimeMillis() - startedAt);
        } catch (Exception ex) {
            String errorMsg = ex.getMessage() != null
                    ? ex.getMessage().substring(0, Math.min(500, ex.getMessage().length()))
                    : "未知错误";
            item.setImageGenStatus("FAILED");
            item.setImageGenErrorMessage(errorMsg);
            slotMatchResultMapper.updateById(item);
            writeImageGenLog(projectId, versionId, item, "FAILED", null, errorMsg);

            eventPublisher.publishEvent(new ImageGenerationCompletedEvent(
                    this, projectId, versionId, item.getMatchId(), item.getSegmentIndex(),
                    false, null, errorMsg
            ));

            log.warn("image generation failed: projectId={}, segmentIndex={}, category={}, error={}",
                    projectId, item.getSegmentIndex(), category, errorMsg);
        }
    }

    /**
     * Download the Seedream-generated image and persist it locally.
     * Seedream URLs may expire; local persistence ensures render availability.
     */
    private String downloadGeneratedImage(String projectId, String versionId, int segmentIndex, String imageUrl) {
        Path outputDir = resolveStorageRoot().resolve("creation-adapt").resolve(projectId).resolve(versionId);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "无法创建生图输出目录: " + ex.getMessage());
        }
        Path localFile = outputDir.resolve(String.format("img_gen_seg_%03d.png", segmentIndex));

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create(imageUrl))
                    .timeout(java.time.Duration.ofSeconds(60))
                    .GET()
                    .build();
            java.net.http.HttpResponse<java.io.InputStream> response = client.send(
                    request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.ARK_API_ERROR,
                        "下载生图失败: status=" + response.statusCode());
            }
            Files.copy(response.body(), localFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("seedream image downloaded: url={} -> localPath={}, size={}",
                    imageUrl, localFile, Files.size(localFile));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "下载生图失败: " + ex.getMessage());
        }
        return localFile.toString();
    }

    /**
     * For categories requiring transparent background, ensure the prompt
     * includes explicit transparency instructions.
     */
    private String enforceTransparencyPrompt(String prompt, String category) {
        if (!"UI_ELEMENT".equals(category) && !"STICKER".equals(category) && !"LOGO".equals(category)) {
            return prompt;
        }
        if (!prompt.toLowerCase().contains("transparent")
                && !prompt.toLowerCase().contains("alpha channel")
                && !prompt.toLowerCase().contains("no background")) {
            return prompt + ", transparent background, no background, isolated on transparent, PNG with alpha channel";
        }
        return prompt;
    }

    /**
     * Negative prompt tailored for transparent-background generation.
     */
    private String transparencyNegativePrompt(String category) {
        if (!"UI_ELEMENT".equals(category) && !"STICKER".equals(category) && !"LOGO".equals(category)) {
            return "low quality, blurry, distorted, extra limbs, bad anatomy, watermark, signature";
        }
        return "solid background, white background, black background, colored background, opaque, JPEG artifacts, watermark text, cluttered background, gradient background, backdrop";
    }

    private Path resolveStorageRoot() {
        return com.bytedance.aivideo.creation.util.StoragePathResolver.resolveStorageRoot();
    }

    private void writeImageGenLog(
            String projectId,
            String versionId,
            SlotMatchResultEntity item,
            String status,
            String imageUrl,
            String errorMessage
    ) {
        CreationFfmpegCommandLogEntity entity = new CreationFfmpegCommandLogEntity();
        entity.setProjectId(projectId);
        entity.setVersionId(versionId);
        entity.setMatchId(item.getMatchId());
        entity.setSegmentIndex(item.getSegmentIndex());
        entity.setMaterialBizId("IMAGE_GEN");
        entity.setStrategyType("SEEDREAM_IMAGE_GEN");
        entity.setStatus(status);
        entity.setCommandText(item.getImageGenPrompt());
        if (errorMessage != null) {
            entity.setErrorMessage(errorMessage);
        }
        if (imageUrl != null) {
            entity.setStdoutTail(imageUrl);
        }
        commandLogMapper.insert(entity);
    }

    private record CommandExecutionResult(int exitCode, long elapsedMs, String stdoutTail, String stderrTail) {
    }

    private record StrategyBuildResult(String strategyType, StrategyExecutionFragment fragment) {
    }
}
