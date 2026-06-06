package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.creation.dto.*;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.mapper.CreationProjectMapper;
import com.bytedance.aivideo.creation.mapper.CreativeMaterialMapper;
import com.bytedance.aivideo.creation.service.BgmEnergyCurveMatchService;
import com.bytedance.aivideo.creation.service.BgmRecommendService;
import com.bytedance.aivideo.creation.service.BgmVectorSearchService;
import com.bytedance.aivideo.infrastructure.vector.BgmVectorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class BgmRecommendServiceImpl implements BgmRecommendService {

    private final CreativeMaterialMapper materialMapper;
    private final CreationProjectMapper projectMapper;
    private final BgmVectorSearchService vectorSearchService;
    private final BgmEnergyCurveMatchService energyCurveMatchService;
    private final BgmVectorProperties bgmVectorProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public BgmRecommendServiceImpl(
            CreativeMaterialMapper materialMapper,
            CreationProjectMapper projectMapper,
            BgmVectorSearchService vectorSearchService,
            BgmEnergyCurveMatchService energyCurveMatchService,
            BgmVectorProperties bgmVectorProperties,
            ObjectMapper objectMapper,
            StringRedisTemplate stringRedisTemplate) {
        this.materialMapper = materialMapper;
        this.projectMapper = projectMapper;
        this.vectorSearchService = vectorSearchService;
        this.energyCurveMatchService = energyCurveMatchService;
        this.bgmVectorProperties = bgmVectorProperties;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public BgmRecommendResponse recommend(String projectId, double w1, double w2, double w3, int topN, boolean forceRefresh) {
        log.info("开始执行智能 BGM 推荐，projectId={}, w1={}, w2={}, w3={}, topN={}", projectId, w1, w2, w3, topN);

        // 1. 查询已提取完毕（PROFILED 状态）的素材画像
        List<CreativeMaterialEntity> materials = materialMapper.selectList(
                new LambdaQueryWrapper<CreativeMaterialEntity>()
                        .eq(CreativeMaterialEntity::getProjectId, projectId)
                        .eq(CreativeMaterialEntity::getStatus, "PROFILED")
                        .isNull(CreativeMaterialEntity::getDeletedAt)
        );

        if (materials.isEmpty()) {
            log.warn("项目内未找到已解析的素材，无法进行 BGM 推荐，projectId={}", projectId);
            return BgmRecommendResponse.builder()
                    .projectId(projectId)
                    .recommendations(new ArrayList<>())
                    .build();
        }

        // 2. 拼接语义检索 query
        StringBuilder queryBuilder = new StringBuilder();
        for (CreativeMaterialEntity material : materials) {
            String profileJson = material.getProfileJson();
            if (profileJson != null && !profileJson.isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(profileJson);
                    JsonNode semanticTags = node.path("semanticTags");
                    if (!semanticTags.isMissingNode()) {
                        String overallStyle = semanticTags.path("overallStyle").asText("");
                        String emotionTone = semanticTags.path("emotionTone").asText("");
                        if (!overallStyle.isBlank()) {
                            queryBuilder.append(overallStyle).append(" ");
                        }
                        if (!emotionTone.isBlank()) {
                            queryBuilder.append(emotionTone).append(" ");
                        }
                        JsonNode keywords = semanticTags.path("mainKeywords");
                        if (keywords.isArray()) {
                            for (JsonNode kw : keywords) {
                                queryBuilder.append(kw.asText()).append(" ");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析素材画像异常，materialId={}", material.getId(), e);
                }
            }
        }

        // 3. 读取项目信息与绑定的模板快照
        CreationProjectEntity project = projectMapper.selectOne(
                new LambdaQueryWrapper<CreationProjectEntity>()
                        .eq(CreationProjectEntity::getProjectId, projectId)
                        .isNull(CreationProjectEntity::getDeletedAt)
        );
        if (project == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "项目不存在");
        }

        String templateJson = project.getTemplateSnapshotJson();
        boolean hasTemplate = templateJson != null && !templateJson.isBlank();

        if (hasTemplate) {
            try {
                // 如果绑定了模板，把模板的描述和核心风格也加入 query 以便向量检索获得更好的效果
                JsonNode tplNode = objectMapper.readTree(templateJson);
                String tplDesc = tplNode.path("description").asText("");
                String tplStyle = tplNode.path("meta").path("overallStyle").asText("");
                if (!tplDesc.isBlank()) {
                    queryBuilder.append(tplDesc).append(" ");
                }
                if (!tplStyle.isBlank()) {
                    queryBuilder.append(tplStyle).append(" ");
                }
            } catch (Exception e) {
                log.warn("解析模板快照异常，projectId={}", projectId, e);
            }
        }

        String cacheKey = "aivideo:recommend:bgm:" + projectId + ":" + topN;
        String cachedJson = forceRefresh ? null : stringRedisTemplate.opsForValue().get(cacheKey);
        List<BgmRecommendItemResponse> items = new ArrayList<>();

        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                items = objectMapper.readValue(
                        cachedJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<BgmRecommendItemResponse>>() {}
                );
                for (BgmRecommendItemResponse item : items) {
                    double semantic = item.getSemanticScore() != null ? item.getSemanticScore() : 0.0;
                    double energy = item.getEnergyCurveScore() != null ? item.getEnergyCurveScore() : 1.0;
                    double durationBpm = item.getDurationBpmScore() != null ? item.getDurationBpmScore() : 1.0;
                    double finalScore = (semantic * w1) + (energy * w2) + (durationBpm * w3);
                    item.setFinalScore(finalScore);
                }
                log.info("BGM recommend cache hit for project: {}, recalculated with w1={}, w2={}, w3={}, topN={}", projectId, w1, w2, w3, topN);
            } catch (Exception e) {
                log.warn("Failed to parse cached BGM recommendations for project: {}", projectId, e);
                items.clear();
            }
        }

        if (items.isEmpty()) {
            String queryText = queryBuilder.toString().trim();
            if (queryText.isEmpty()) {
                queryText = "video background music rhythm beat"; // 兜底 Query
            }

            // 4. Chroma 向量数据库检索 TopK BGM 候选集
            List<BgmCandidate> candidates = vectorSearchService.searchCandidates(queryText, Math.max(topN * 2, 20));

            // 5. 遍历候选 BGM 列表，解析对应的本地 audio_data.json 并打分
            for (BgmCandidate candidate : candidates) {
            try {
                // 读取本地 audio_data.json
                File folder = findAudioFolder(candidate.getFolderName());
                if (folder == null) {
                    log.warn("未找到对应的音频目录: {}", candidate.getFolderName());
                    continue;
                }

                File jsonFile = new File(folder, "audio_data.json");
                if (!jsonFile.exists()) {
                    log.warn("音频目录中不存在 audio_data.json: {}", jsonFile.getAbsolutePath());
                    continue;
                }

                String audioDataJson = Files.readString(jsonFile.toPath());
                JsonNode audioRoot = objectMapper.readTree(audioDataJson);
                String relFilePath = audioRoot.path("filePath").asText("");
                if (relFilePath.isEmpty()) {
                    // 兜底找 wav 或 mp3
                    File[] files = folder.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            String name = f.getName().toLowerCase(Locale.ROOT);
                            if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".m4a")) {
                                relFilePath = f.getName();
                                break;
                            }
                        }
                    }
                }

                // 统一返回给前端的相对 URL 路径：/api/storage/audio-database/{folderName}/{fileName}
                String finalUrlPath = "/api/storage/audio-database/" + candidate.getFolderName() + "/" + relFilePath;

                // 得分计算
                double semanticScore = candidate.getSemanticScore();
                double energyCurveScore = 1.0;
                double durationBpmScore = 1.0;

                if (hasTemplate) {
                    // 1. 结构与 Hard Veto 打分
                    energyCurveScore = energyCurveMatchService.calculateEnergyCurveScore(audioDataJson, templateJson);
                    if (energyCurveScore < 0) {
                        log.info("BGM {} 触发一票否决(Hard Veto)，直接过滤", candidate.getAudioId());
                        continue; // 被 Veto，剔除
                    }

                    // 2. 时长与 BPM 物理特征匹配打分
                    // 计算模板的总时长：将 segments 每一个段落的 minDuration 累加起来
                    double templateTotalDuration = 0.0;
                    try {
                        JsonNode segmentsNode = objectMapper.readTree(templateJson).path("scriptStructure").path("segments");
                        if (segmentsNode.isArray()) {
                            for (JsonNode segNode : segmentsNode) {
                                double minDur = segNode.path("durationRange").path("min").asDouble(3.0);
                                templateTotalDuration += minDur;
                            }
                        }
                    } catch (Exception e) {}

                    if (templateTotalDuration > 0) {
                        double bgmDuration = candidate.getDurationSeconds();
                        double ratio = bgmDuration / templateTotalDuration;
                        // 若 BGM 长度大于模板总长，视作完美覆盖，得分 1.0；否则根据比例折算，最低 0.4 分
                        durationBpmScore = ratio >= 1.0 ? 1.0 : Math.max(0.4, ratio);
                    }
                }

                // 综合打分 = w1 * 语义 + w2 * 能量 + w3 * 物理对齐
                double finalScore = (semanticScore * w1) + (energyCurveScore * w2) + (durationBpmScore * w3);

                items.add(BgmRecommendItemResponse.builder()
                        .audioId(candidate.getAudioId())
                        .audioName(candidate.getAudioName())
                        .bpm(candidate.getBpm())
                        .overallStyle(candidate.getOverallStyle())
                        .durationSeconds(candidate.getDurationSeconds())
                        .filePath(finalUrlPath)
                        .semanticScore(semanticScore)
                        .energyCurveScore(energyCurveScore)
                        .durationBpmScore(durationBpmScore)
                        .finalScore(finalScore)
                        .build());

            } catch (Exception e) {
                log.error("计算音频推荐得分失败，audioId={}", candidate.getAudioId(), e);
            }
        }

            // Save to cache
            try {
                String jsonToCache = objectMapper.writeValueAsString(items);
                stringRedisTemplate.opsForValue().set(cacheKey, jsonToCache, 24, java.util.concurrent.TimeUnit.HOURS);
                log.info("Cached BGM recommendations for project: {}", projectId);
            } catch (Exception e) {
                log.warn("Failed to cache BGM recommendations for project: {}", projectId, e);
            }
        }

        // 6. 综合得分降序排序，取 Top N，并注入 Rank
        items.sort(Comparator.comparing(BgmRecommendItemResponse::getFinalScore).reversed());
        List<BgmRecommendItemResponse> resultList = new ArrayList<>();
        int rank = 1;
        for (BgmRecommendItemResponse item : items) {
            item.setRank(rank++);
            resultList.add(item);
            if (resultList.size() >= topN) {
                break;
            }
        }

        log.info("智能 BGM 推荐完成，共推荐 {} 个项", resultList.size());
        return BgmRecommendResponse.builder()
                .projectId(projectId)
                .recommendations(resultList)
                .build();
    }

    private File findAudioFolder(String folderName) {
        String baseDir = bgmVectorProperties.getAudioDatabaseDir();
        // 尝试直接相对路径
        File f = new File(baseDir, folderName);
        if (f.exists() && f.isDirectory()) {
            return f;
        }
        // 尝试上一级相对路径 fallback
        f = new File("../" + baseDir, folderName);
        if (f.exists() && f.isDirectory()) {
            return f;
        }
        // 尝试当前用户工作目录
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            java.nio.file.Path p = java.nio.file.Paths.get(userDir).resolve(baseDir).resolve(folderName);
            if (p.toFile().exists() && p.toFile().isDirectory()) {
                return p.toFile();
            }
            p = java.nio.file.Paths.get(userDir).getParent().resolve(baseDir).resolve(folderName);
            if (p.toFile().exists() && p.toFile().isDirectory()) {
                return p.toFile();
            }
        }
        return null;
    }
}
