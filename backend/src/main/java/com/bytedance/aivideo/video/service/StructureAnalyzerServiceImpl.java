package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.infrastructure.ark.ArkPayloadFactory;
import com.bytedance.aivideo.infrastructure.ark.ArkPromptTemplates;
import com.bytedance.aivideo.infrastructure.ark.ArkResponsesClient;
import com.bytedance.aivideo.infrastructure.ark.model.ArkInputMessage;
import com.bytedance.aivideo.infrastructure.ark.model.ArkResponseRequest;
import com.bytedance.aivideo.deconstruct.service.DeconstructTemplateService;
import com.bytedance.aivideo.video.dto.timeline.TimelineMatchResult;
import com.bytedance.aivideo.video.entity.CategoryKnowledgeEntity;
import com.bytedance.aivideo.video.entity.KeyFrameEntity;
import com.bytedance.aivideo.video.mapper.CategoryKnowledgeMapper;
import com.bytedance.aivideo.video.mapper.KeyFrameMapper;
import com.bytedance.aivideo.video.util.TimelinePromptFormatter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class StructureAnalyzerServiceImpl implements StructureAnalyzerService {
    private static final String DEFAULT_CATEGORY_ID = "general_content";
    private static final String DEFAULT_CATEGORY_NAME = "通用内容类";

    private static final Set<String> MACRO_CATEGORY_WHITELIST = Set.of(
            "short_drama", "tutorial", "product_review", "vlog", "gameplay", "marketing", "editing", "motion_graphics"
    );

    private static final Map<String, String> CATEGORY_ALIAS_MAP = new HashMap<>();

    private static final Set<String> MACRO_FIELD_NAME_HINTS = Set.of(
            "threshold", "frequency", "ratio", "rate", "distribution", "density",
            "strategy", "style", "transition", "motion", "cut", "bpm", "sync",
            "pace", "hook", "selling", "cta", "socialProof", "shot", "scene"
    );

    private static final Set<String> SCENARIO_LEAK_KEYWORDS = Set.of(
            "食堂", "校园", "学校", "地铁站", "教室", "宿舍", "办公室", "商场", "停车场", "工地",
            "canteen", "campus", "classroom", "dorm", "office", "mall", "station"
    );

    private static final Set<String> PERSON_LEAK_KEYWORDS = Set.of(
            "阿姨", "老师", "同学", "博主", "up主", "店员", "老板", "妈妈", "爸爸", "朋友",
            "author", "creator", "nickname", "account", "idol", "teacher", "classmate"
    );

    private static final Set<String> PLOT_LEAK_KEYWORDS = Set.of(
            "打架", "吵架", "分手", "逆袭", "告白", "抢救", "剧情", "反转", "台词",
            "fight", "breakup", "plot", "twist", "dialogue", "confession"
    );

    static {
        CATEGORY_ALIAS_MAP.put("school_canteen_short_drama", "short_drama");
        CATEGORY_ALIAS_MAP.put("school_canteen_drama", "short_drama");
        CATEGORY_ALIAS_MAP.put("car_review", "product_review");
        CATEGORY_ALIAS_MAP.put("auto_review", "product_review");
    }

    private final ArkPayloadFactory arkPayloadFactory;
    private final ArkResponsesClient arkResponsesClient;
    private final CategoryKnowledgeMapper categoryKnowledgeMapper;
    private final TimelineMatcherService timelineMatcherService;
    private final KeyFrameMapper keyFrameMapper;
    private final VideoAnalysisResultService videoAnalysisResultService;
    private final DeconstructTemplateService deconstructTemplateService;
    private final VideoTaskStageService videoTaskStageService;
    private final ObjectMapper objectMapper;
    private final com.bytedance.aivideo.config.ArkProperties arkProperties;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public StructureAnalyzerServiceImpl(ArkPayloadFactory arkPayloadFactory,
                                        ArkResponsesClient arkResponsesClient,
                                        CategoryKnowledgeMapper categoryKnowledgeMapper,
                                        TimelineMatcherService timelineMatcherService,
                                        KeyFrameMapper keyFrameMapper,
                                        VideoAnalysisResultService videoAnalysisResultService,
                                        DeconstructTemplateService deconstructTemplateService,
                                        VideoTaskStageService videoTaskStageService,
                                        ObjectMapper objectMapper,
                                        com.bytedance.aivideo.config.ArkProperties arkProperties,
                                        VectorStore vectorStore,
                                        EmbeddingModel embeddingModel) {
        this.arkPayloadFactory = arkPayloadFactory;
        this.arkResponsesClient = arkResponsesClient;
        this.categoryKnowledgeMapper = categoryKnowledgeMapper;
        this.timelineMatcherService = timelineMatcherService;
        this.keyFrameMapper = keyFrameMapper;
        this.videoAnalysisResultService = videoAnalysisResultService;
        this.deconstructTemplateService = deconstructTemplateService;
        this.videoTaskStageService = videoTaskStageService;
        this.objectMapper = objectMapper;
        this.arkProperties = arkProperties;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public AnalysisOutput analyzeAndRefine(String taskId, TimelineMatchResult fatTimelineResult, List<KeyFrameEntity> keyFrames) {
        // 1. 加载图片 Base64，并构建与发送顺序一致的 IMAGE 标注映射
        List<String> base64Images = new ArrayList<>();
        Map<String, String> imageTagByPath = new LinkedHashMap<>();
        int imageOrder = 1;
        if (keyFrames != null && !keyFrames.isEmpty()) {
            for (KeyFrameEntity kf : keyFrames) {
                if (kf.getFilePath() != null) {
                    try {
                        Path imgPath = Paths.get(kf.getFilePath());
                        if (Files.exists(imgPath)) {
                            byte[] bytes = Files.readAllBytes(imgPath);
                            base64Images.add(Base64.getEncoder().encodeToString(bytes));
                            String normalizedPath = imgPath.toAbsolutePath().normalize().toString();
                            String imageTag = String.format("【IMAGE_%03d】", imageOrder++);
                            imageTagByPath.put(kf.getFilePath(), imageTag);
                            imageTagByPath.put(normalizedPath, imageTag);
                        }
                    } catch (IOException e) {
                        log.warn("Failed to read keyframe image: {}", kf.getFilePath(), e);
                    }
                }
            }
        }

        // 2. 生成 Timeline Markdown 剧本（含关键帧 IMAGE 标签）
        String timelineMarkdown = TimelinePromptFormatter.formatToMarkdown(fatTimelineResult, imageTagByPath);

        // 3. 构建已知品类知识库上下文 (RAG Vector Search)
        String semanticQuery = buildSemanticQuery(fatTimelineResult);
        String categoryContext = buildCategoryContextRAG(semanticQuery);

        // 4. 组装 prompt 并调用大模型
        String prompt = String.format(ArkPromptTemplates.STRUCTURE_ANALYZER_UNIFIED, timelineMarkdown, categoryContext);
        
        List<ArkInputMessage> messages = arkPayloadFactory.buildMultimodalInput(base64Images, prompt);
        
        ArkResponseRequest request = new ArkResponseRequest();
        request.setModel(arkProperties.getModel());
        request.setMessages(messages);

        JsonNode responseNode = arkResponsesClient.createResponse(request);

        // 5. 解析 LLM 返回的大一统 JSON
        String llmContent = extractLlmContent(responseNode);
        JsonNode unifiedJson;
        try {
            unifiedJson = objectMapper.readTree(llmContent);
        } catch (Exception e) {
            log.error("llm structure json parse failed: taskId={}, content={}", taskId, llmContent, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "LLM 返回的结构模板解析失败");
        }

        CategorySanitizeResult sanitizeResult = sanitizeCategoryExtensions(taskId, unifiedJson);
        String sanitizedLlmContent;
        try {
            sanitizedLlmContent = objectMapper.writeValueAsString(unifiedJson);
        } catch (Exception e) {
            log.error("llm structure json serialize failed after sanitize: taskId={}", taskId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "结构模板清洗后序列化失败");
        }

        // 6. 热注册新品类 / 更新场景提纯阈值，并落库闭环 + Chroma 同步
        double sceneThreshold = 0.25; // 默认值
        try {
            if (sanitizeResult.categoryExtNode() != null) {
                JsonNode ext = sanitizeResult.categoryExtNode();
                String catId = sanitizeResult.normalizedCategoryId();
                String catName = sanitizeResult.normalizedCategoryName();
                
                CategoryKnowledgeEntity catEntity = categoryKnowledgeMapper.selectById(catId);
                if (catEntity == null) {
                    catEntity = new CategoryKnowledgeEntity();
                    catEntity.setCategoryId(catId);
                    catEntity.setCategoryName(catName);
                    catEntity.setDiscoveredByLlm(true);
                    catEntity.setConfidenceScore(0.9);
                    catEntity.setUsageCount(1);
                    sceneThreshold = extractThreshold(ext);
                    catEntity.setSceneThreshold(sceneThreshold);
                    
                    if (ext.has("dynamicExtensionFields")) {
                        catEntity.setDynamicFields(ext.get("dynamicExtensionFields").toString());
                    }
                    if (ext.has("discoveredPromptOverrides")) {
                        catEntity.setPromptOverrides(ext.get("discoveredPromptOverrides").toString());
                    }
                    categoryKnowledgeMapper.insert(catEntity);
                    log.info("Hot-registered new video category: {} with threshold {}", catId, sceneThreshold);
                } else {
                    catEntity.setUsageCount(catEntity.getUsageCount() + 1);
                    sceneThreshold = catEntity.getSceneThreshold() != null ? catEntity.getSceneThreshold() : 0.25;
                    // 执行增量增强算法
                    mergeDynamicFields(catEntity, ext);
                    categoryKnowledgeMapper.updateById(catEntity);
                }
                // 同步特征至 VectorStore
                syncToVectorStore(catEntity);
                
            } else if (sanitizeResult.normalizedCategoryId() != null && !sanitizeResult.normalizedCategoryId().isBlank()) {
                String catId = sanitizeResult.normalizedCategoryId();
                CategoryKnowledgeEntity catEntity = categoryKnowledgeMapper.selectById(catId);
                if (catEntity != null && catEntity.getSceneThreshold() != null) {
                    sceneThreshold = catEntity.getSceneThreshold();
                    catEntity.setUsageCount(catEntity.getUsageCount() + 1);
                    categoryKnowledgeMapper.updateById(catEntity);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to process category hot registration and vector sync. Fallback to default threshold.", e);
        }

        // 7. 使用获得的品类专属 sceneThreshold，二次提纯 Timeline
        TimelineMatchResult refinedTimeline = timelineMatcherService.match(taskId, sceneThreshold);
        
        return AnalysisOutput.builder()
                .videoStructureTemplateJson(sanitizedLlmContent) // 使用清洗后的 JSON 落库
                .refinedTimeline(refinedTimeline)
                .build();
    }

    private String extractLlmContent(JsonNode responseNode) {
        try {
            JsonNode choices = responseNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    String content = message.get("content").asText();
                    // 清理 markdown 的 ```json ... ``` 包装
                    if (content.startsWith("```json")) {
                        content = content.substring(7);
                    } else if (content.startsWith("```")) {
                        content = content.substring(3);
                    }
                    if (content.endsWith("```")) {
                        content = content.substring(0, content.length() - 3);
                    }
                    return content.trim();
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract content from LLM response.", e);
        }
        return "{}";
    }

    private String buildSemanticQuery(TimelineMatchResult fatTimelineResult) {
        if (fatTimelineResult == null || fatTimelineResult.getTimelineSegments() == null) {
            return "";
        }
        
        double maxCuttingVelocity = 0.0;
        List<String> resonances = new ArrayList<>();
        StringBuilder asrBuilder = new StringBuilder();
        
        for (com.bytedance.aivideo.video.dto.timeline.TimelineSegment segment : fatTimelineResult.getTimelineSegments()) {
            if (segment.getVisualDynamics() != null && segment.getVisualDynamics().getCuttingVelocity() > maxCuttingVelocity) {
                maxCuttingVelocity = segment.getVisualDynamics().getCuttingVelocity();
            }
            if (segment.getAudioVisualResonance() != null && !segment.getAudioVisualResonance().isBlank()) {
                resonances.add(segment.getAudioVisualResonance());
            }
            if (segment.getAudioAndText() != null) {
                for (com.bytedance.aivideo.video.dto.timeline.AudioTextEntry entry : segment.getAudioAndText()) {
                    if (entry.getText() != null && !entry.getText().isBlank()) {
                        asrBuilder.append(entry.getText()).append(" ");
                    }
                }
            }
        }
        
        String resonanceStr = String.join("、", resonances);
        String asrStr = asrBuilder.toString();
        // 截断 ASR 防止单次查询文本过大
        if (asrStr.length() > 600) {
            asrStr = asrStr.substring(0, 600) + "...";
        }
        
        List<Double> waveform = fatTimelineResult.getSystemMeta() != null ? fatTimelineResult.getSystemMeta().getVisualWaveform() : null;
        String waveformStr = waveform != null ? waveform.toString() : "[]";
        String visualFeature = String.format("切分速率峰值 %.2f次/秒", maxCuttingVelocity);
        if (!resonanceStr.isEmpty()) {
            visualFeature += "，共振: " + resonanceStr;
        }
        
        return String.format("[画面波形: %s] [视听特征: %s] [文本: %s]", waveformStr, visualFeature, asrStr);
    }

    private String buildCategoryContextRAG(String semanticQuery) {
        if (semanticQuery == null || semanticQuery.isBlank()) {
            return "当前暂无特征查询。";
        }
        
        try {
            // 通过 Spring AI VectorStore 检索 Top 3 品类特征
            List<Document> topDocs = vectorStore.similaritySearch(
                SearchRequest.builder().query(semanticQuery).topK(3).build()
            );
            if (topDocs == null || topDocs.isEmpty()) {
                return "当前暂无匹配的知识库历史数据。";
            }
            
            StringBuilder sb = new StringBuilder();
            for (Document doc : topDocs) {
                String catId = (String) doc.getMetadata().get("categoryId");
                String catName = (String) doc.getMetadata().get("categoryName");
                sb.append("- ").append(catId != null ? catId : "未知").append(" (").append(catName != null ? catName : "未知").append(")\n");
                sb.append("  提取特征: ").append(doc.getText()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Vector search failed, fallback to empty context.", e);
            return "向量检索失败，暂无可用先验知识。";
        }
    }

    private double extractThreshold(JsonNode ext) {
        if (ext.has("dynamicExtensionFields") && ext.get("dynamicExtensionFields").isArray()) {
            for (JsonNode field : ext.get("dynamicExtensionFields")) {
                if ("scene_threshold".equals(field.get("fieldName").asText())) {
                    return field.get("fieldValue").asDouble(0.25);
                }
            }
        }
        return 0.25;
    }

    private void mergeDynamicFields(CategoryKnowledgeEntity dbCat, JsonNode newExt) {
        if (!newExt.has("dynamicExtensionFields") || !newExt.get("dynamicExtensionFields").isArray()) {
            return;
        }
        try {
            JsonNode newFields = newExt.get("dynamicExtensionFields");
            if (dbCat.getDynamicFields() == null || dbCat.getDynamicFields().isBlank()) {
                dbCat.setDynamicFields(newFields.toString());
                return;
            }
            
            // 旧字段解析为 List
            List<JsonNode> oldFieldsList = new ArrayList<>();
            JsonNode oldFields = objectMapper.readTree(dbCat.getDynamicFields());
            if (oldFields.isArray()) {
                oldFields.forEach(oldFieldsList::add);
            }
            
            // 合并逻辑：同名字段按类型兼容更新；新字段追加；冲突字段跳过并记录
            for (JsonNode newField : newFields) {
                String newFieldName = newField.has("fieldName") ? newField.get("fieldName").asText() : null;
                if (newFieldName == null) continue;
                int existingIdx = -1;
                for (int i = 0; i < oldFieldsList.size(); i++) {
                    JsonNode old = oldFieldsList.get(i);
                    if (old.has("fieldName") && newFieldName.equals(old.get("fieldName").asText())) {
                        existingIdx = i;
                        break;
                    }
                }
                if (existingIdx < 0) {
                    oldFieldsList.add(newField);
                    continue;
                }

                JsonNode oldField = oldFieldsList.get(existingIdx);
                String oldType = oldField.has("fieldType") ? oldField.get("fieldType").asText("") : "";
                String newType = newField.has("fieldType") ? newField.get("fieldType").asText("") : "";
                if (!oldType.equalsIgnoreCase(newType)) {
                    log.warn("dynamic field type mismatch skipped: categoryId={}, fieldName={}, oldType={}, newType={}",
                            dbCat.getCategoryId(), newFieldName, oldType, newType);
                    continue;
                }

                JsonNode merged = mergeFieldNode(oldField, newField);
                oldFieldsList.set(existingIdx, merged);
            }
            dbCat.setDynamicFields(objectMapper.writeValueAsString(oldFieldsList));
        } catch (Exception e) {
            log.warn("Failed to merge dynamic fields for category {}", dbCat.getCategoryId(), e);
        }
    }

    private JsonNode mergeFieldNode(JsonNode oldField, JsonNode newField) {
        if (!(oldField instanceof ObjectNode oldObj) || !(newField instanceof ObjectNode newObj)) {
            return newField;
        }
        JsonNode oldValue = oldObj.get("fieldValue");
        JsonNode newValue = newObj.get("fieldValue");
        if (oldValue != null && oldValue.isArray() && newValue != null && newValue.isArray()) {
            Set<String> dedup = new HashSet<>();
            ArrayNode merged = objectMapper.createArrayNode();
            for (JsonNode v : oldValue) {
                String key = v.toString();
                if (dedup.add(key)) {
                    merged.add(v);
                }
            }
            for (JsonNode v : newValue) {
                String key = v.toString();
                if (dedup.add(key)) {
                    merged.add(v);
                }
            }
            oldObj.set("fieldValue", merged);
            if (newObj.has("description")) {
                oldObj.set("description", newObj.get("description"));
            }
            return oldObj;
        }
        return newField;
    }

    private CategorySanitizeResult sanitizeCategoryExtensions(String taskId, JsonNode unifiedJson) {
        ObjectNode root = unifiedJson instanceof ObjectNode ? (ObjectNode) unifiedJson : objectMapper.createObjectNode();
        ObjectNode ext = root.has("categoryExtensions") && root.get("categoryExtensions").isObject()
                ? (ObjectNode) root.get("categoryExtensions")
                : objectMapper.createObjectNode();
        if (!root.has("categoryExtensions")) {
            root.set("categoryExtensions", ext);
        }

        String rawCategoryId = ext.has("discoveredCategoryId")
                ? ext.get("discoveredCategoryId").asText("")
                : root.path("category").asText("");
        String normalizedCategoryId = normalizeMacroCategoryId(rawCategoryId);
        if (normalizedCategoryId == null || normalizedCategoryId.isBlank()) {
            normalizedCategoryId = DEFAULT_CATEGORY_ID;
        }

        String rawCategoryName = ext.has("discoveredCategoryName")
                ? ext.get("discoveredCategoryName").asText("")
                : normalizedCategoryId;
        String normalizedCategoryName = normalizeMacroCategoryName(normalizedCategoryId, rawCategoryName);

        root.put("category", normalizedCategoryId);
        ext.put("discoveredCategoryId", normalizedCategoryId);
        ext.put("discoveredCategoryName", normalizedCategoryName);

        if (!normalizedCategoryId.equals(rawCategoryId)) {
            log.info("category normalized: taskId={}, rawCategoryId={}, normalizedCategoryId={}", taskId, rawCategoryId, normalizedCategoryId);
        }

        ArrayNode sanitizedFields = objectMapper.createArrayNode();
        List<DiscoveryFieldCandidate> rejected = new ArrayList<>();
        JsonNode rawFields = ext.get("dynamicExtensionFields");
        if (rawFields != null && rawFields.isArray()) {
            for (JsonNode field : rawFields) {
                if (!(field instanceof ObjectNode fieldObj)) {
                    rejected.add(DiscoveryFieldCandidate.reject(taskId, normalizedCategoryId, "", "FORMAT_INVALID", field));
                    continue;
                }
                String fieldName = fieldObj.path("fieldName").asText("").trim();
                String fieldType = fieldObj.path("fieldType").asText("").trim().toUpperCase(Locale.ROOT);
                JsonNode fieldValue = fieldObj.get("fieldValue");

                if (fieldName.isBlank() || fieldType.isBlank() || fieldValue == null) {
                    rejected.add(DiscoveryFieldCandidate.reject(taskId, normalizedCategoryId, fieldName, "FORMAT_INVALID", fieldObj));
                    continue;
                }
                if (containsLeak(fieldName) || containsLeak(fieldObj.path("description").asText("")) || containsLeak(fieldValue.toString())) {
                    rejected.add(DiscoveryFieldCandidate.reject(taskId, normalizedCategoryId, fieldName, detectLeakReason(fieldObj), fieldObj));
                    continue;
                }
                if (!isMacroFeatureField(fieldName)) {
                    rejected.add(DiscoveryFieldCandidate.reject(taskId, normalizedCategoryId, fieldName, "NON_MACRO_FEATURE", fieldObj));
                    continue;
                }

                ObjectNode normalizedField = fieldObj.deepCopy();
                normalizedField.put("fieldName", fieldName);
                normalizedField.put("fieldType", fieldType);
                sanitizedFields.add(normalizedField);
            }
        }
        ext.set("dynamicExtensionFields", sanitizedFields);

        if (!rejected.isEmpty()) {
            for (DiscoveryFieldCandidate candidate : rejected) {
                log.info("category field rejected: taskId={}, categoryId={}, fieldName={}, conflictReason={}, status={}",
                        candidate.taskId(), candidate.categoryId(), candidate.fieldName(), candidate.conflictReason(), candidate.status());
            }
        }
        log.info("category sanitize finished: taskId={}, categoryId={}, acceptedFieldCount={}, rejectedFieldCount={}",
                taskId, normalizedCategoryId, sanitizedFields.size(), rejected.size());

        return new CategorySanitizeResult(normalizedCategoryId, normalizedCategoryName, ext);
    }

    private String normalizeMacroCategoryId(String rawCategoryId) {
        if (rawCategoryId == null || rawCategoryId.isBlank()) {
            return DEFAULT_CATEGORY_ID;
        }
        String normalized = rawCategoryId.trim().toLowerCase(Locale.ROOT);
        if (CATEGORY_ALIAS_MAP.containsKey(normalized)) {
            return CATEGORY_ALIAS_MAP.get(normalized);
        }
        if (MACRO_CATEGORY_WHITELIST.contains(normalized)) {
            return normalized;
        }

        if (normalized.contains("review")) {
            return "product_review";
        }
        if (normalized.contains("drama") || normalized.contains("story") || normalized.contains("skit")) {
            return "short_drama";
        }
        if (normalized.contains("tutorial") || normalized.contains("teach") || normalized.contains("how_to")) {
            return "tutorial";
        }
        if (normalized.contains("vlog")) {
            return "vlog";
        }
        if (normalized.contains("game") || normalized.contains("gameplay")) {
            return "gameplay";
        }
        return normalized;
    }

    private String normalizeMacroCategoryName(String categoryId, String rawName) {
        return switch (categoryId) {
            case "short_drama" -> "微短剧";
            case "tutorial" -> "教学科普";
            case "product_review" -> "产品测评";
            case "vlog" -> "日常记录";
            case "gameplay" -> "游戏实况";
            case "marketing" -> "营销类";
            case "editing" -> "剪辑类";
            case "motion_graphics" -> "MG/动态海报类";
            case "general_content" -> "通用内容类";
            default -> (rawName == null || rawName.isBlank()) ? categoryId : rawName;
        };
    }

    private boolean isMacroFeatureField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        for (String hint : MACRO_FIELD_NAME_HINTS) {
            if (normalized.contains(hint.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return normalized.endsWith("count")
                || normalized.endsWith("ratio")
                || normalized.endsWith("rate")
                || normalized.endsWith("threshold");
    }

    private boolean containsLeak(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String value = text.toLowerCase(Locale.ROOT);
        return hitKeyword(value, SCENARIO_LEAK_KEYWORDS)
                || hitKeyword(value, PERSON_LEAK_KEYWORDS)
                || hitKeyword(value, PLOT_LEAK_KEYWORDS);
    }

    private String detectLeakReason(ObjectNode fieldObj) {
        String all = (
                fieldObj.path("fieldName").asText("") + " " +
                fieldObj.path("description").asText("") + " " +
                String.valueOf(fieldObj.path("fieldValue"))
        ).toLowerCase(Locale.ROOT);
        if (hitKeyword(all, SCENARIO_LEAK_KEYWORDS)) {
            return "SCENARIO_LEAK";
        }
        if (hitKeyword(all, PERSON_LEAK_KEYWORDS)) {
            return "PERSON_LEAK";
        }
        if (hitKeyword(all, PLOT_LEAK_KEYWORDS)) {
            return "PLOT_LEAK";
        }
        return "LEAK";
    }

    private boolean hitKeyword(String value, Set<String> keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record CategorySanitizeResult(
            String normalizedCategoryId,
            String normalizedCategoryName,
            ObjectNode categoryExtNode
    ) {
    }

    private record DiscoveryFieldCandidate(
            String taskId,
            String categoryId,
            String fieldName,
            String conflictReason,
            String status,
            JsonNode rawPayload
    ) {
        static DiscoveryFieldCandidate reject(String taskId, String categoryId, String fieldName, String conflictReason, JsonNode rawPayload) {
            return new DiscoveryFieldCandidate(taskId, categoryId, fieldName, conflictReason, "REJECTED", rawPayload);
        }
    }

    private String inferFieldValueShape(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return "null";
        }
        if (valueNode.isArray()) {
            return "array";
        }
        if (valueNode.isObject()) {
            return "object";
        }
        return "scalar";
    }

    private void syncToVectorStore(CategoryKnowledgeEntity catEntity) {
        try {
            if (catEntity.getDynamicFields() == null || catEntity.getDynamicFields().isBlank()) {
                return;
            }
            
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("品类: ").append(catEntity.getCategoryName()).append("\n");
            
            JsonNode fields = objectMapper.readTree(catEntity.getDynamicFields());
            if (fields.isArray()) {
                for (JsonNode field : fields) {
                    String name = field.has("fieldName") ? field.get("fieldName").asText() : "";
                    String type = field.has("fieldType") ? field.get("fieldType").asText("").toUpperCase(Locale.ROOT) : "UNKNOWN";
                    String shape = inferFieldValueShape(field.get("fieldValue"));
                    String desc = field.has("description") ? field.get("description").asText() : "";
                    if (!name.isBlank()) {
                        contentBuilder.append("- ").append(name)
                                .append(" (").append(type).append(", ").append(shape).append(")")
                                .append(": ").append(desc).append("\n");
                    }
                }
            }
            
            Map<String, Object> metadata = Map.of(
                "categoryId", catEntity.getCategoryId(),
                "categoryName", catEntity.getCategoryName() != null ? catEntity.getCategoryName() : ""
            );
            
            // Chroma 会自动覆盖具有相同 ID 的文档（取决于 VectorStore 的具体实现，通常以 id 为主键）
            Document doc = new Document(catEntity.getCategoryId(), contentBuilder.toString(), metadata);
            vectorStore.add(List.of(doc));
            log.info("Synced category {} to VectorStore", catEntity.getCategoryId());
        } catch (Exception e) {
            log.error("Failed to sync category {} to VectorStore", catEntity.getCategoryId(), e);
        }
    }

    @Override
    public AnalysisOutput triggerLlmAnalysis(String taskId) {
        videoTaskStageService.initStageIfAbsent(taskId, VideoTaskStageService.STAGE_TYPE_TIMELINE);
        videoTaskStageService.initStageIfAbsent(taskId, VideoTaskStageService.STAGE_TYPE_LLM);
        
        videoTaskStageService.markRunning(taskId, VideoTaskStageService.STAGE_TYPE_TIMELINE);
        // 1. 获取胖数据
        TimelineMatchResult fatTimeline;
        try {
            fatTimeline = timelineMatcherService.match(taskId, 0.15); // 默认高敏
            if (fatTimeline == null) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "无法生成高敏胖数据，请检查前期结果");
            }
            videoTaskStageService.markSuccess(taskId, VideoTaskStageService.STAGE_TYPE_TIMELINE);
        } catch (Exception e) {
            videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_TIMELINE, e.getMessage());
            throw e;
        }

        videoTaskStageService.markRunning(taskId, VideoTaskStageService.STAGE_TYPE_LLM);
        // 2. 加载关键帧
        List<KeyFrameEntity> keyFrames = keyFrameMapper.selectList(
                new LambdaQueryWrapper<KeyFrameEntity>()
                        .eq(KeyFrameEntity::getTaskId, taskId)
                        .isNull(KeyFrameEntity::getDeletedAt)
                        .orderByAsc(KeyFrameEntity::getFrameIndex)
        );

        // 3. 执行核心分析与精修
        AnalysisOutput output;
        try {
            output = analyzeAndRefine(taskId, fatTimeline, keyFrames);
            videoTaskStageService.markSuccess(taskId, VideoTaskStageService.STAGE_TYPE_LLM);
        } catch (Exception e) {
            videoTaskStageService.markFailed(taskId, VideoTaskStageService.STAGE_TYPE_LLM, e.getMessage());
            throw e;
        }

        // 4. 落库保存结果（仅 timeline 资产，避免覆盖 core/text）
        try {
            String fatTimelineJson = objectMapper.writeValueAsString(fatTimeline);
            String refinedTimelineJson = objectMapper.writeValueAsString(output.getRefinedTimeline());
            videoAnalysisResultService.saveLlmTimelineResult(
                    taskId,
                    fatTimelineJson,
                    refinedTimelineJson,
                    output.getVideoStructureTemplateJson()
            );
            log.info("llm timeline saved: taskId={}", taskId);
        } catch (Exception e) {
            log.error("llm timeline save failed: taskId={}", taskId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to save llm timeline result: " + e.getMessage());
        }

        try {
            deconstructTemplateService.publishFromTask(taskId, output.getVideoStructureTemplateJson());
        } catch (Exception ex) {
            log.warn("template publish failed after timeline saved: taskId={}", taskId, ex);
        }

        return output;
    }
}
