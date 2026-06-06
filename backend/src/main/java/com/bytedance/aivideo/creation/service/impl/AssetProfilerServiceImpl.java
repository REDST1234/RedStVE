package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.FfmpegCommandProperties;
import com.bytedance.aivideo.creation.dto.profile.AssetProfileDto;
import com.bytedance.aivideo.creation.dto.profile.HighlightSegmentDto;
import com.bytedance.aivideo.creation.dto.profile.LightingProfileDto;
import com.bytedance.aivideo.creation.dto.profile.PhysicalAttributesDto;
import com.bytedance.aivideo.creation.dto.profile.SemanticTagsDto;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.entity.CreativeMaterialGridEntity;
import com.bytedance.aivideo.creation.mapper.CreativeMaterialGridMapper;
import com.bytedance.aivideo.creation.mapper.CreativeMaterialMapper;
import com.bytedance.aivideo.creation.service.AssetProfilerService;
import com.bytedance.aivideo.creation.service.CreationAsrService;
import com.bytedance.aivideo.creation.service.MultimodalLlmService;
import com.bytedance.aivideo.creation.dto.profile.CreationAsrResult;
import com.bytedance.aivideo.creation.dto.profile.CreationAsrSegment;
import com.bytedance.aivideo.creation.util.LlmJsonRepairUtils;
import com.bytedance.aivideo.engine.ffmpeg.api.AudioExtractEngine;
import com.bytedance.aivideo.engine.ffmpeg.api.CreationFfmpegEngine;
import com.bytedance.aivideo.engine.ffmpeg.api.MediaProbeEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.CreationGridPageResult;
import com.bytedance.aivideo.engine.ffmpeg.model.LuminanceDetectResult;
import com.bytedance.aivideo.engine.ffmpeg.model.MediaProbeResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetProfilerServiceImpl implements AssetProfilerService {

    private static final String MATERIAL_TYPE_VIDEO = "VIDEO";
    private static final String MATERIAL_TYPE_IMAGE = "IMAGE";
    private static final String MATERIAL_TYPE_TEXT = "TEXT";
    private static final String MATERIAL_STATUS_PROFILING = "PROFILING";
    private static final String MATERIAL_STATUS_PROFILED = "PROFILED";
    private static final String MATERIAL_STATUS_FAILED = "FAILED";
    private static final String GRID_STATUS_READY = "READY";

    private final CreativeMaterialMapper materialMapper;
    private final CreativeMaterialGridMapper materialGridMapper;
    private final MediaProbeEngine ffprobeEngine;
    private final CreationFfmpegEngine creationFfmpegEngine;
    private final AudioExtractEngine audioExtractEngine;
    private final CreationAsrService creationAsrService;
    private final MultimodalLlmService multimodalLlmService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FfmpegCommandProperties ffmpegCommandProperties;

    @Async("videoTaskExecutor")
    @Override
    public void profileAsset(String assetId) {
        CreativeMaterialEntity material = materialMapper.selectOne(new LambdaQueryWrapper<CreativeMaterialEntity>()
                .eq(CreativeMaterialEntity::getBizId, Long.parseLong(assetId))
                .isNull(CreativeMaterialEntity::getDeletedAt)
                .last("LIMIT 1"));
        if (material == null) {
            log.error("Asset not found for id: {}", assetId);
            return;
        }

        material.setStatus(MATERIAL_STATUS_PROFILING);
        materialMapper.updateById(material);

        try {
            AssetProfileDto profile = new AssetProfileDto();
            profile.setBizId(material.getBizId());
            profile.setMaterialType(material.getMaterialType());
            profile.setSourcePath(material.getFilePath());
            LuminanceDetectResult luminanceResultForLog = null;

            Path sourcePath = null;
            if (material.getFilePath() != null && !material.getFilePath().isBlank()) {
                sourcePath = Paths.get(material.getFilePath());
            }

            PhysicalAttributesDto physical = new PhysicalAttributesDto();
            physical.setFileSize(material.getFileSize());

            if (MATERIAL_TYPE_VIDEO.equalsIgnoreCase(material.getMaterialType())) {
                MediaProbeResult probeResult = ffprobeEngine.probe(sourcePath);
                physical.setTotalDurationSeconds(probeResult.getDuration());
                physical.setResolution(buildResolution(probeResult.getWidth(), probeResult.getHeight()));
                physical.setAspectRatio(probeResult.getWidth() + ":" + probeResult.getHeight());
                physical.setFps(probeResult.getFps() != null ? probeResult.getFps().intValue() : 30);
                physical.setHasAudio(probeResult.getAudioCodec() != null);

                LuminanceDetectResult luminanceResult = creationFfmpegEngine.detectLuminance(sourcePath);
                luminanceResultForLog = luminanceResult;
                Double luminance = luminanceResult.getNormalizedLuminance();
                LightingProfileDto lighting = new LightingProfileDto();
                lighting.setMeanLuminance(luminance);
                lighting.setDiagnosisStrategy(luminanceResult.isFallbackUsed()
                        ? "stage_1_ffmpeg_fast_fallback"
                        : "stage_1_ffmpeg_fast");
                lighting.setContrastRatio(luminanceResult.getContrastRatio());
                if (luminance < 0.1) {
                    lighting.setIsUnderExposed(true);
                } else if (luminance > 0.9) {
                    lighting.setIsOverExposed(true);
                }
                physical.setLighting(lighting);

                Path gridOutputDir = sourcePath.getParent().resolve("grids");
                if (!Files.exists(gridOutputDir)) {
                    Files.createDirectories(gridOutputDir);
                }

                int cols;
                int rows;
                if (probeResult.getWidth() > probeResult.getHeight()) {
                    cols = 2;
                    rows = 4;
                } else {
                    cols = 4;
                    rows = 2;
                }

                cleanupExistingGridAssets(material.getBizId());

                int fps = 1;
                int maxPages = Math.max(1, ffmpegCommandProperties.getGridMaxPages());
                final Path finalSourcePath = sourcePath;
                final int finalCols = cols;
                final int finalRows = rows;
                final Double finalDuration = probeResult.getDuration() == null ? 1.0 : probeResult.getDuration();

                CompletableFuture<List<CreationGridPageResult>> gridTask = CompletableFuture.supplyAsync(() -> {
                    return creationFfmpegEngine.generateGridSequencePages(
                            finalSourcePath,
                            gridOutputDir,
                            material.getBizId() + "_grid",
                            fps,
                            finalCols,
                            finalRows,
                            512,
                            finalDuration,
                            maxPages
                    );
                });

                CompletableFuture<CreationAsrResult> asrTask = CompletableFuture.supplyAsync(() -> {
                    if (Boolean.TRUE.equals(physical.getHasAudio())) {
                        try {
                            Path audioOutputDir = finalSourcePath.getParent().resolve("audio");
                            Path audioPath = audioExtractEngine.extractToMp3(finalSourcePath, audioOutputDir, material.getBizId() + ".mp3");
                            log.info("Audio extracted for {} at {}", material.getBizId(), audioPath);
                            return creationAsrService.transcribe(audioPath);
                        } catch (Exception ex) {
                            log.warn("Audio ASR task failed for bizId {}: {}", material.getBizId(), ex.getMessage());
                            return null;
                        }
                    }
                    return null;
                });

                CompletableFuture.allOf(gridTask, asrTask).join();
                
                List<CreationGridPageResult> gridPages = gridTask.join();
                CreationAsrResult asrResult = asrTask.join();

                if (gridPages == null || gridPages.isEmpty()) {
                    throw new BizException(com.bytedance.aivideo.common.error.ErrorCode.FFMPEG_ERROR, "Grid pages empty");
                }
                
                profile.setAsrResult(asrResult); // 第一时间将结构化结果落入 profile_json

                List<Path> gridImagePaths = new ArrayList<>();
                List<Map<String, Object>> redisPageMeta = new ArrayList<>();
                List<CreativeMaterialGridEntity> persistedGridRows = new ArrayList<>();
                int framesPerPage = cols * rows;
                for (CreationGridPageResult page : gridPages) {
                    Path pagePath = page.getOutputPath();
                    gridImagePaths.add(pagePath);

                    String base64Img = Base64.getEncoder().encodeToString(Files.readAllBytes(pagePath));
                    String pageCacheKey = "creation:asset:grid:" + material.getBizId() + ":" + page.getPageIndex();
                    redisTemplate.opsForValue().set(pageCacheKey, base64Img, 1, TimeUnit.HOURS);

                    Map<String, Object> pageMeta = new LinkedHashMap<>();
                    pageMeta.put("pageIndex", page.getPageIndex());
                    pageMeta.put("status", GRID_STATUS_READY);
                    pageMeta.put("filePath", pagePath.toString());
                    pageMeta.put("cacheKey", pageCacheKey);
                    pageMeta.put("llmIncluded", false);
                    redisPageMeta.add(pageMeta);

                    CreativeMaterialGridEntity gridEntity = new CreativeMaterialGridEntity();
                    gridEntity.setMaterialBizId(material.getBizId());
                    gridEntity.setPageIndex(page.getPageIndex());
                    gridEntity.setFilePath(pagePath.toString());
                    gridEntity.setFrameCount(page.getFrameCount() <= 0 ? framesPerPage : page.getFrameCount());
                    gridEntity.setFps(fps);
                    gridEntity.setGridCols(cols);
                    gridEntity.setGridRows(rows);
                    gridEntity.setStatus(GRID_STATUS_READY);
                    gridEntity.setLlmIncluded(false);
                    materialGridMapper.insert(gridEntity);
                    persistedGridRows.add(gridEntity);
                }
                redisTemplate.opsForValue().set(
                        "creation:asset:grid:" + material.getBizId() + ":pages",
                        objectMapper.writeValueAsString(redisPageMeta),
                        1,
                        TimeUnit.HOURS
                );

                Optional<String> asrTextOpt = Optional.empty();
                if (asrResult != null && asrResult.getSegments() != null) {
                    StringBuilder sb = new StringBuilder();
                    for (CreationAsrSegment seg : asrResult.getSegments()) {
                        sb.append(String.format("[%.1f-%.1f] %s\n", seg.getStart(), seg.getEnd(), seg.getText()));
                    }
                    if (!sb.isEmpty()) {
                        asrTextOpt = Optional.of(sb.toString().trim());
                    }
                }

                boolean llmDegraded = runLlmAnalysisWithFallback(
                        profile,
                        MATERIAL_TYPE_VIDEO,
                        gridImagePaths,
                        asrTextOpt,
                        null,
                        physical,
                        material.getBizId()
                );
                if (!llmDegraded) {
                    for (CreativeMaterialGridEntity row : persistedGridRows) {
                        row.setLlmIncluded(true);
                        materialGridMapper.updateById(row);
                    }
                    for (Map<String, Object> meta : redisPageMeta) {
                        meta.put("llmIncluded", true);
                    }
                }
                redisTemplate.opsForValue().set(
                        "creation:asset:grid:" + material.getBizId() + ":pages",
                        objectMapper.writeValueAsString(redisPageMeta),
                        1,
                        TimeUnit.HOURS
                );
            } else if (MATERIAL_TYPE_IMAGE.equalsIgnoreCase(material.getMaterialType())) {
                physical.setResolution(buildResolution(material.getWidth(), material.getHeight()));
                physical.setAspectRatio(buildAspectRatio(material.getWidth(), material.getHeight()));
                runLlmAnalysisWithFallback(
                        profile,
                        MATERIAL_TYPE_IMAGE,
                        sourcePath == null ? Collections.emptyList() : List.of(sourcePath),
                        Optional.empty(),
                        null,
                        physical,
                        material.getBizId()
                );
            } else if (MATERIAL_TYPE_TEXT.equalsIgnoreCase(material.getMaterialType())) {
                String text = material.getTextContent() == null ? "" : material.getTextContent();
                physical.setCharCount(text.length());
                physical.setParagraphCount(countParagraphs(text));
                runLlmAnalysisWithFallback(
                        profile,
                        MATERIAL_TYPE_TEXT,
                        Collections.emptyList(),
                        Optional.empty(),
                        text,
                        physical,
                        material.getBizId()
                );
            }

            profile.setPhysicalAttributes(physical);

            if (profile.getSemanticTags() == null) {
                SemanticTagsDto semantic = new SemanticTagsDto();
                semantic.setOverallStyle("Fallback Style");
                profile.setSemanticTags(semantic);
            }
            if (profile.getHighlights() == null) {
                profile.setHighlights(Collections.emptyList());
            }

            String profileJson = buildSanitizedProfileJson(profile);
            material.setProfileJson(profileJson);
            logPhysicalAttributes(material.getBizId(), material.getMaterialType(), profileJson, luminanceResultForLog, physical);
            material.setStatus(MATERIAL_STATUS_PROFILED);
            materialMapper.updateById(material);
            clearRecommendationCache(material.getProjectId());

        } catch (Exception e) {
            log.error("Failed to profile asset: {}", assetId, e);
            cleanupExistingGridAssets(material.getBizId());
            material.setStatus(MATERIAL_STATUS_FAILED);
            materialMapper.updateById(material);
            clearRecommendationCache(material.getProjectId());
        }
    }

    private void clearRecommendationCache(String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            String pId = projectId.trim();
            // 缓存 key 包含 topN 后缀，需用模式匹配删除所有 topN 变体
            var templateKeys = redisTemplate.keys("aivideo:recommend:template:" + pId + ":*");
            if (templateKeys != null && !templateKeys.isEmpty()) {
                redisTemplate.delete(templateKeys);
            }
            var bgmKeys = redisTemplate.keys("aivideo:recommend:bgm:" + pId + ":*");
            if (bgmKeys != null && !bgmKeys.isEmpty()) {
                redisTemplate.delete(bgmKeys);
            }
            log.info("Cleared recommendation cache for project: {}", pId);
        }
    }

    private boolean runLlmAnalysisWithFallback(
            AssetProfileDto profile,
            String materialType,
            List<Path> imagePaths,
            Optional<String> asrText,
            String textContent,
            PhysicalAttributesDto physicalAttributes,
            Long materialBizId
    ) {
        try {
            String llmResult = multimodalLlmService.analyzeProfile(
                    materialType,
                    imagePaths,
                    asrText,
                    textContent,
                    physicalAttributes
            );
            applyLlmProfileResult(profile, materialType, llmResult);
            log.info("creation profile llm completed: materialBizId={}, materialType={}, llmDegraded=false", materialBizId, materialType);
            return false;
        } catch (Exception ex) {
            log.warn("creation profile llm degraded: materialBizId={}, materialType={}, llmDegraded=true, reason={}",
                    materialBizId, materialType, ex.getMessage());
            SemanticTagsDto semantic = new SemanticTagsDto();
            semantic.setOverallStyle("Fallback Style");
            if (MATERIAL_TYPE_TEXT.equalsIgnoreCase(materialType)) {
                semantic.setTextCategory("general");
            }
            profile.setSemanticTags(semantic);
            profile.setHighlights(Collections.emptyList());
            return true;
        }
    }

    private void applyLlmProfileResult(AssetProfileDto profile, String materialType, String llmResult) throws Exception {
        JsonNode root = parseMultimodalLlmJson(llmResult);
        if (root.hasNonNull("semanticTags")) {
            profile.setSemanticTags(objectMapper.treeToValue(root.get("semanticTags"), SemanticTagsDto.class));
        } else {
            profile.setSemanticTags(objectMapper.treeToValue(root, SemanticTagsDto.class));
        }

        if (root.has("highlights") && root.get("highlights").isArray()) {
            List<HighlightSegmentDto> highlights = objectMapper.convertValue(
                    root.get("highlights"),
                    new TypeReference<List<HighlightSegmentDto>>() {
                    }
            );
            profile.setHighlights(filterHighlightsByMaterialType(highlights, materialType));
        } else {
            profile.setHighlights(Collections.emptyList());
        }
    }

    private JsonNode parseMultimodalLlmJson(String llmResult) throws Exception {
        try {
            return objectMapper.readTree(llmResult);
        } catch (Exception firstEx) {
            String repaired = LlmJsonRepairUtils.normalizeJsonObjectText(llmResult);
            if (repaired.equals(llmResult == null ? "" : llmResult.trim())) {
                log.warn("parse multimodal llm result degraded: reason={}", firstEx.getMessage());
                throw firstEx;
            }
            try {
                JsonNode repairedRoot = objectMapper.readTree(repaired);
                log.warn("multimodal llm json repaired: originalLength={}, repairedLength={}, reason={}",
                        llmResult == null ? 0 : llmResult.length(),
                        repaired.length(),
                        firstEx.getMessage());
                return repairedRoot;
            } catch (Exception secondEx) {
                log.warn("parse multimodal llm result degraded after repair: firstReason={}, secondReason={}",
                        firstEx.getMessage(), secondEx.getMessage());
                throw secondEx;
            }
        }
    }

    private List<HighlightSegmentDto> filterHighlightsByMaterialType(List<HighlightSegmentDto> highlights, String materialType) {
        if (highlights == null || highlights.isEmpty()) {
            return Collections.emptyList();
        }
        List<HighlightSegmentDto> filtered = new ArrayList<>();
        String normalizedType = materialType == null ? "" : materialType.trim().toUpperCase();
        for (HighlightSegmentDto item : highlights) {
            if (item == null) {
                continue;
            }
            if (MATERIAL_TYPE_VIDEO.equals(normalizedType)) {
                if (item.getTimeAnchor() == null
                        || item.getTimeAnchor().getStartTime() == null
                        || item.getTimeAnchor().getEndTime() == null
                        || item.getTimeAnchor().getEndTime() <= item.getTimeAnchor().getStartTime()) {
                    continue;
                }
            } else if (MATERIAL_TYPE_IMAGE.equals(normalizedType)) {
                item.setTimeAnchor(null);
            } else if (MATERIAL_TYPE_TEXT.equals(normalizedType)) {
                item.setTimeAnchor(null);
                item.setSpatialAnchor(null);
            }
            filtered.add(item);
        }
        return filtered;
    }

    private void cleanupExistingGridAssets(Long materialBizId) {
        List<CreativeMaterialGridEntity> oldGridRows = materialGridMapper.selectList(
                new LambdaQueryWrapper<CreativeMaterialGridEntity>()
                        .eq(CreativeMaterialGridEntity::getMaterialBizId, materialBizId)
                        .isNull(CreativeMaterialGridEntity::getDeletedAt)
                        .orderByAsc(CreativeMaterialGridEntity::getPageIndex)
        );

        for (CreativeMaterialGridEntity row : oldGridRows) {
            try {
                if (row.getFilePath() != null && !row.getFilePath().isBlank()) {
                    Files.deleteIfExists(Paths.get(row.getFilePath()));
                }
            } catch (Exception ex) {
                log.warn("delete old grid file failed: materialBizId={}, pageIndex={}, reason={}",
                        materialBizId, row.getPageIndex(), ex.getMessage());
            }
            if (row.getPageIndex() != null) {
                redisTemplate.delete("creation:asset:grid:" + materialBizId + ":" + row.getPageIndex());
            }
        }

        if (!oldGridRows.isEmpty()) {
            materialGridMapper.delete(new LambdaQueryWrapper<CreativeMaterialGridEntity>()
                    .eq(CreativeMaterialGridEntity::getMaterialBizId, materialBizId)
                    .isNull(CreativeMaterialGridEntity::getDeletedAt));
        }
        redisTemplate.delete("creation:asset:grid:" + materialBizId);
        redisTemplate.delete("creation:asset:grid:" + materialBizId + ":pages");
    }

    private String buildSanitizedProfileJson(AssetProfileDto profile) throws Exception {
        ObjectNode root = objectMapper.valueToTree(profile);
        ObjectNode physicalNode = asObjectNode(root.get("physicalAttributes"));
        if (physicalNode != null) {
            String materialType = profile.getMaterialType() == null ? "" : profile.getMaterialType().trim().toUpperCase();
            if (MATERIAL_TYPE_VIDEO.equals(materialType)) {
                retainOnly(physicalNode, Set.of(
                        "fileSize", "totalDurationSeconds", "resolution", "aspectRatio", "fps", "hasAudio", "lighting"
                ));
                ObjectNode lightingNode = asObjectNode(physicalNode.get("lighting"));
                if (lightingNode != null) {
                    retainOnly(lightingNode, Set.of(
                            "meanLuminance", "contrastRatio", "isOverExposed", "isUnderExposed", "diagnosisStrategy"
                    ));
                }
            } else if (MATERIAL_TYPE_IMAGE.equals(materialType)) {
                retainOnly(physicalNode, Set.of("fileSize", "resolution", "aspectRatio"));
            } else if (MATERIAL_TYPE_TEXT.equals(materialType)) {
                retainOnly(physicalNode, Set.of("fileSize", "charCount", "paragraphCount"));
            }
        }
        pruneNullAndEmptyObjects(root);
        return objectMapper.writeValueAsString(root);
    }

    private void retainOnly(ObjectNode node, Set<String> allowedFields) {
        List<String> currentFields = new ArrayList<>();
        node.fieldNames().forEachRemaining(currentFields::add);
        for (String field : currentFields) {
            if (!allowedFields.contains(field)) {
                node.remove(field);
            }
        }
    }

    private ObjectNode asObjectNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        return null;
    }

    private boolean pruneNullAndEmptyObjects(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<String> fields = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                JsonNode child = objectNode.get(field);
                if (child == null || child.isNull()) {
                    objectNode.remove(field);
                    continue;
                }
                boolean childEmpty = pruneNullAndEmptyObjects(child);
                if (childEmpty && child instanceof ObjectNode) {
                    objectNode.remove(field);
                }
            }
            return objectNode.isEmpty();
        }
        if (node instanceof ArrayNode arrayNode) {
            for (int i = arrayNode.size() - 1; i >= 0; i--) {
                JsonNode child = arrayNode.get(i);
                if (child == null || child.isNull()) {
                    arrayNode.remove(i);
                    continue;
                }
                boolean childEmpty = pruneNullAndEmptyObjects(child);
                if (childEmpty && child instanceof ObjectNode) {
                    arrayNode.remove(i);
                }
            }
            return false;
        }
        return false;
    }

    private int countParagraphs(String text) {
        if (text == null) {
            return 0;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        String[] paragraphs = normalized.split("(\\r?\\n\\s*){2,}");
        int count = 0;
        for (String paragraph : paragraphs) {
            if (!paragraph.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private String buildAspectRatio(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null;
        }
        int gcd = gcd(width, height);
        return (width / gcd) + ":" + (height / gcd);
    }

    private String buildResolution(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null;
        }
        return width + "x" + height;
    }

    private int gcd(int a, int b) {
        int x = Math.abs(a);
        int y = Math.abs(b);
        if (x == 0) {
            return y == 0 ? 1 : y;
        }
        while (y != 0) {
            int t = x % y;
            x = y;
            y = t;
        }
        return x == 0 ? 1 : x;
    }

    private void logPhysicalAttributes(
            Long materialBizId,
            String materialType,
            String profileJson,
            LuminanceDetectResult luminanceResult,
            PhysicalAttributesDto physical
    ) {
        try {
            JsonNode root = objectMapper.readTree(profileJson);
            JsonNode physicalNode = root.get("physicalAttributes");
            List<String> filledFields = new ArrayList<>();
            if (physicalNode instanceof ObjectNode physicalObject) {
                physicalObject.fieldNames().forEachRemaining(field -> {
                    filledFields.add("physicalAttributes." + field);
                    JsonNode child = physicalObject.get(field);
                    if (child instanceof ObjectNode childObject) {
                        childObject.fieldNames().forEachRemaining(subField ->
                                filledFields.add("physicalAttributes." + field + "." + subField));
                    }
                });
            }
            log.info("profile physical attributes completed: materialBizId={}, materialType={}, meanLuminance={}, contrastRatio={}, fallbackUsed={}, matchedPatternType={}, physicalFilledFields={}",
                    materialBizId,
                    materialType,
                    physical != null && physical.getLighting() != null ? physical.getLighting().getMeanLuminance() : null,
                    physical != null && physical.getLighting() != null ? physical.getLighting().getContrastRatio() : null,
                    luminanceResult != null && luminanceResult.isFallbackUsed(),
                    luminanceResult != null ? luminanceResult.getMatchedPatternType() : "N/A",
                    filledFields);
        } catch (Exception ex) {
            log.warn("profile physical attributes log degraded: materialBizId={}, reason={}", materialBizId, ex.getMessage());
        }
    }
}
