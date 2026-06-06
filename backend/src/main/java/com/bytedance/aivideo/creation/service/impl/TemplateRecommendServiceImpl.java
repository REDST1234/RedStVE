package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.creation.dto.TemplateCandidate;
import com.bytedance.aivideo.creation.dto.TemplateRecommendItemResponse;
import com.bytedance.aivideo.creation.dto.TemplateRecommendResponse;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.mapper.CreativeMaterialMapper;
import com.bytedance.aivideo.creation.service.TemplateRecommendService;
import com.bytedance.aivideo.creation.service.TemplateStructureMatchService;
import com.bytedance.aivideo.creation.service.TemplateVectorSearchService;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructTemplateMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class TemplateRecommendServiceImpl implements TemplateRecommendService {

    private final CreativeMaterialMapper creativeMaterialMapper;
    private final TemplateVectorSearchService templateVectorSearchService;
    private final TemplateStructureMatchService templateStructureMatchService;
    private final DeconstructTemplateMapper deconstructTemplateMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public TemplateRecommendServiceImpl(
            CreativeMaterialMapper creativeMaterialMapper,
            TemplateVectorSearchService templateVectorSearchService,
            TemplateStructureMatchService templateStructureMatchService,
            DeconstructTemplateMapper deconstructTemplateMapper,
            ObjectMapper objectMapper,
            StringRedisTemplate stringRedisTemplate) {
        this.creativeMaterialMapper = creativeMaterialMapper;
        this.templateVectorSearchService = templateVectorSearchService;
        this.templateStructureMatchService = templateStructureMatchService;
        this.deconstructTemplateMapper = deconstructTemplateMapper;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public TemplateRecommendResponse recommend(String projectId, double w1, double w2, int topN, boolean forceRefresh) {
        // 1. 获取项目所有 PROFILED 状态的素材
        List<CreativeMaterialEntity> materials = creativeMaterialMapper.selectList(
                new LambdaQueryWrapper<CreativeMaterialEntity>()
                        .eq(CreativeMaterialEntity::getProjectId, projectId)
                        .eq(CreativeMaterialEntity::getStatus, "PROFILED")
        );

        if (materials == null || materials.isEmpty()) {
            return TemplateRecommendResponse.builder()
                    .projectId(projectId)
                    .recommendations(new ArrayList<>())
                    .build();
        }

        String cacheKey = "aivideo:recommend:template:" + projectId + ":" + topN;
        log.info("Template recommend: projectId={}, w1={}, w2={}, topN={}, forceRefresh={}, cacheKey={}",
                projectId, w1, w2, topN, forceRefresh, cacheKey);
        String cachedJson = forceRefresh ? null : stringRedisTemplate.opsForValue().get(cacheKey);
        List<TemplateRecommendItemResponse> scoredItems = new ArrayList<>();

        if (cachedJson != null && !cachedJson.isBlank()) {
            log.info(">>> CACHE HIT for key={}, items={}", cacheKey, "will re-weight");
            try {
                scoredItems = objectMapper.readValue(
                        cachedJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<TemplateRecommendItemResponse>>() {}
                );
                for (TemplateRecommendItemResponse item : scoredItems) {
                    double finalScore = (item.getSemanticScore() * w1) + (item.getStructureScore() * w2);
                    item.setFinalScore(finalScore);
                }
                log.info("Template recommend cache hit for project: {}, recalculated with w1={}, w2={}, topN={}", projectId, w1, w2, topN);
            } catch (Exception e) {
                log.warn("Failed to parse cached template recommendations for project: {}", projectId, e);
                scoredItems.clear();
            }
        }

        if (scoredItems.isEmpty()) {
            log.info(">>> CACHE MISS or forceRefresh, computing fresh for key={}", cacheKey);
            // 2. 聚合素材画像为查询文本
            String queryText = buildQueryTextFromMaterials(materials);
            log.debug("Project {} semantic search query: {}", projectId, queryText);

            // 3. 向量检索获取候选 (获取足够多以便过滤)
            List<TemplateCandidate> candidates = templateVectorSearchService.searchCandidates(queryText, topN * 2);

            // 4 & 5. 计算结构得分并合并
            for (TemplateCandidate candidate : candidates) {
                DeconstructTemplateEntity template = deconstructTemplateMapper.selectOne(
                        new LambdaQueryWrapper<DeconstructTemplateEntity>()
                                .eq(DeconstructTemplateEntity::getTemplateId, candidate.getTemplateId())
                                .eq(DeconstructTemplateEntity::getTemplateVersion, candidate.getTemplateVersion())
                                .last("LIMIT 1")
                );

                if (template == null || template.getTemplateJson() == null) continue;

                String templateKey = candidate.getTemplateId() + "_v" + candidate.getTemplateVersion();
                String categoryId = candidate.getCategoryId();
                double structureScore = templateStructureMatchService.calculateStructureScore(
                        materials,
                        template.getTemplateJson(),
                        templateKey,
                        categoryId
                );
                
                // Veto 过滤
                if (structureScore < 0) {
                    log.info("Template {} vetoed for project {}", candidate.getTemplateId(), projectId);
                    continue;
                }

                double finalScore = (candidate.getSemanticScore() * w1) + (structureScore * w2);
                log.info("template candidate scored: projectId={}, templateKey={}, semanticScore={}, structureScore={}, finalScore={}",
                        projectId, templateKey, candidate.getSemanticScore(), structureScore, finalScore);

                scoredItems.add(TemplateRecommendItemResponse.builder()
                        .templateId(candidate.getTemplateId())
                        .templateVersion(candidate.getTemplateVersion())
                        .templateName(template.getTemplateName())
                        .categoryId(candidate.getCategoryId())
                        .segmentCount(candidate.getSegmentCount())
                        .semanticScore(candidate.getSemanticScore())
                        .structureScore(structureScore)
                        .finalScore(finalScore)
                        .build());
            }

            // Save to cache
            try {
                String jsonToCache = objectMapper.writeValueAsString(scoredItems);
                stringRedisTemplate.opsForValue().set(cacheKey, jsonToCache, 24, java.util.concurrent.TimeUnit.HOURS);
                log.info("Cached template recommendations for project: {}", projectId);
            } catch (Exception e) {
                log.warn("Failed to cache template recommendations for project: {}", projectId, e);
            }
        }

        // 6. 降序排列并截取 Top N
        scoredItems.sort(Comparator.comparing(TemplateRecommendItemResponse::getFinalScore).reversed());
        if (scoredItems.size() > topN) {
            scoredItems = scoredItems.subList(0, topN);
        }

        for (int i = 0; i < scoredItems.size(); i++) {
            scoredItems.get(i).setRank(i + 1);
        }

        return TemplateRecommendResponse.builder()
                .projectId(projectId)
                .recommendations(scoredItems)
                .build();
    }

    private String buildQueryTextFromMaterials(List<CreativeMaterialEntity> materials) {
        StringBuilder sb = new StringBuilder();
        for (CreativeMaterialEntity m : materials) {
            try {
                if (m.getProfileJson() != null) {
                    JsonNode semanticTags = objectMapper.readTree(m.getProfileJson()).path("semanticTags");
                    if (!semanticTags.isMissingNode()) {
                        sb.append(semanticTags.toString()).append(" ");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse profile json for material {}", m.getId(), e);
            }
        }
        return sb.toString();
    }
}
