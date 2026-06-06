package com.bytedance.aivideo.infrastructure.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructTemplateMapper;
import com.bytedance.aivideo.video.entity.CategoryKnowledgeEntity;
import com.bytedance.aivideo.video.mapper.CategoryKnowledgeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 向量数据初始化服务，将 MySQL / 文件系统中的数据同步至 Chroma 向量库。
 * <p>
 * 不再在启动时自动执行；改为通过 {@code VectorInitController} 或 {@code scripts/vector-init.sh} 手动触发。
 */
@Slf4j
@Service
public class VectorInitService {

    private static final Set<String> LEGACY_INVALID_CATEGORY_IDS = Set.of(
            "school_canteen_short_drama",
            "school_canteen_drama",
            "car_review"
    );

    private final DeconstructTemplateMapper templateMapper;
    private final CategoryKnowledgeMapper categoryKnowledgeMapper;
    private final VectorStore templateVectorStore;
    private final VectorStore bgmVectorStore;
    private final VectorStore categoryVectorStore;
    private final BgmVectorProperties bgmVectorProperties;
    private final ObjectMapper objectMapper;

    public VectorInitService(
            DeconstructTemplateMapper templateMapper,
            CategoryKnowledgeMapper categoryKnowledgeMapper,
            @Qualifier("creationTemplateVectorStore") VectorStore templateVectorStore,
            @Qualifier("creationBgmVectorStore") VectorStore bgmVectorStore,
            @Qualifier("vectorStore") VectorStore categoryVectorStore,
            BgmVectorProperties bgmVectorProperties,
            ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.categoryKnowledgeMapper = categoryKnowledgeMapper;
        this.templateVectorStore = templateVectorStore;
        this.bgmVectorStore = bgmVectorStore;
        this.categoryVectorStore = categoryVectorStore;
        this.bgmVectorProperties = bgmVectorProperties;
        this.objectMapper = objectMapper;
    }

    /** 执行全部三类向量数据同步。 */
    public Map<String, Object> syncAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templates", safeSync("模板向量", this::syncTemplates));
        result.put("bgm", safeSync("BGM向量", this::syncBgmAssets));
        result.put("categoryKnowledge", safeSync("品类知识向量", this::syncCategoryKnowledge));
        boolean allOk = result.values().stream().allMatch(v -> v instanceof String s && s.startsWith("OK"));
        result.put("success", allOk);
        return result;
    }

    // ---- 模板向量同步 ----

    public String syncTemplates() {
        log.info("开始同步 PUBLISHED 模板至 Chroma creation_template...");
        List<DeconstructTemplateEntity> entities = templateMapper.selectList(
                new LambdaQueryWrapper<DeconstructTemplateEntity>()
                        .eq(DeconstructTemplateEntity::getStatus, "PUBLISHED"));
        if (entities == null || entities.isEmpty()) {
            return "OK: 无 PUBLISHED 模板，跳过";
        }

        Map<String, List<DeconstructTemplateEntity>> grouped = entities.stream()
                .collect(java.util.stream.Collectors.groupingBy(DeconstructTemplateEntity::getTemplateId));

        List<Document> documents = new ArrayList<>();
        List<String> idsToDelete = new ArrayList<>();

        for (Map.Entry<String, List<DeconstructTemplateEntity>> entry : grouped.entrySet()) {
            List<DeconstructTemplateEntity> versions = entry.getValue();
            for (DeconstructTemplateEntity v : versions) {
                idsToDelete.add("tpl_" + v.getTemplateId() + "_v" + v.getTemplateVersion());
            }
            DeconstructTemplateEntity latest = versions.stream()
                    .max(Comparator.comparing(DeconstructTemplateEntity::getTemplateVersion))
                    .orElse(null);
            if (latest == null || latest.getTemplateJson() == null || latest.getTemplateJson().isBlank()) {
                continue;
            }
            try {
                Document doc = buildTemplateDocument(latest);
                if (doc != null) {
                    documents.add(doc);
                }
            } catch (Exception e) {
                log.error("同步模板失败: templateId={}", latest.getTemplateId(), e);
            }
        }

        if (!idsToDelete.isEmpty()) {
            try { templateVectorStore.delete(idsToDelete); } catch (Exception e) { log.warn("清理旧模板版本异常: {}", e.getMessage()); }
        }
        if (!documents.isEmpty()) {
            templateVectorStore.add(documents);
        }
        return "OK: 同步 " + documents.size() + " 个模板";
    }

    private Document buildTemplateDocument(DeconstructTemplateEntity entity) throws Exception {
        JsonNode root = objectMapper.readTree(entity.getTemplateJson());
        StringBuilder sb = new StringBuilder();
        sb.append("模板名: ").append(entity.getTemplateName()).append("\n");
        sb.append("品类: ").append(entity.getCategoryId()).append("\n");

        JsonNode meta = root.path("meta");
        if (meta.has("description")) sb.append("说明: ").append(meta.get("description").asText()).append("\n");
        if (meta.has("acousticEnvironment")) sb.append("声场环境: ").append(meta.get("acousticEnvironment").asText()).append("\n");
        if (meta.has("styles") && meta.get("styles").isArray()) {
            sb.append("风格: ");
            for (JsonNode s : meta.get("styles")) sb.append(s.asText()).append(" ");
            sb.append("\n");
        }

        JsonNode segments = root.path("scriptStructure").path("segments");
        if (segments.isArray() && !segments.isEmpty()) {
            sb.append("镜头数: ").append(segments.size()).append("\n");
            List<String> roles = new ArrayList<>();
            for (JsonNode seg : segments) {
                if (seg.has("role")) roles.add(seg.get("role").asText());
            }
            sb.append("角色序列: ").append(String.join(" → ", roles)).append("\n");
            for (JsonNode seg : segments) {
                int idx = seg.path("segmentIndex").asInt(0);
                String role = seg.path("role").asText("unknown");
                double minDur = seg.path("durationRange").path("min").asDouble(0);
                double maxDur = seg.path("durationRange").path("max").asDouble(0);
                sb.append(String.format("- 第%d段(%s): %.1f-%.1f秒", idx, role, minDur, maxDur));
                if (seg.has("description")) sb.append(", ").append(seg.get("description").asText());
                appendNonBlank(sb, ", 镜头偏好: ", joinArray(seg.path("preferredShotTypes")));
                appendNonBlank(sb, ", 运镜: ", joinArray(seg.path("preferredCameraMovements")));
                sb.append("\n");
            }
        }

        JsonNode shots = root.path("shots");
        if (shots.isArray() && !shots.isEmpty()) {
            List<String> shotTypes = new ArrayList<>(), movements = new ArrayList<>(), functions = new ArrayList<>();
            for (JsonNode shot : shots) {
                String t = shot.path("shotTypeTag").asText("");
                String m = shot.path("cameraMovementTag").asText("");
                String f = shot.path("functionHint").asText("");
                if (!t.isBlank()) shotTypes.add(t);
                if (!m.isBlank()) movements.add(m);
                if (!f.isBlank()) functions.add(f);
            }
            if (!shotTypes.isEmpty()) sb.append("镜头原型: ").append(String.join(", ", shotTypes)).append("\n");
            if (!movements.isEmpty()) sb.append("运镜原型: ").append(String.join(", ", movements)).append("\n");
            if (!functions.isEmpty()) sb.append("功能提示: ").append(String.join(", ", functions)).append("\n");
        }

        Map<String, Object> metadata = Map.of(
                "templateId", entity.getTemplateId(),
                "templateVersion", entity.getTemplateVersion(),
                "categoryId", entity.getCategoryId() != null ? entity.getCategoryId() : "unknown",
                "segmentCount", root.path("scriptStructure").path("totalSegments").asInt(0)
        );
        return new Document("tpl_" + entity.getTemplateId(), sb.toString(), metadata);
    }

    // ---- BGM 向量同步 ----

    public String syncBgmAssets() {
        log.info("开始同步 BGM 资产至 Chroma creation_bgm...");
        File dbDir = findBgmDatabaseDir();
        if (dbDir == null || !dbDir.exists() || !dbDir.isDirectory()) {
            return "OK: 未找到 BGM 音频数据库目录，跳过";
        }
        File[] subDirs = dbDir.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            return "OK: BGM 目录为空，跳过";
        }

        List<Document> documents = new ArrayList<>();
        for (File subDir : subDirs) {
            File jsonFile = new File(subDir, "audio_data.json");
            if (!jsonFile.isFile()) continue;
            try {
                JsonNode root = objectMapper.readTree(jsonFile);
                String audioId = root.path("audioId").asText("");
                if (audioId.isBlank()) continue;

                JsonNode gm = root.path("globalMetrics");
                StringBuilder sb = new StringBuilder();
                sb.append("音频名: ").append(root.path("audioName").asText("")).append("\n");
                sb.append("风格: ").append(gm.path("overallStyle").asText("unknown")).append("\n");
                sb.append("BPM: ").append(gm.path("bpm").asInt(120)).append("\n");
                sb.append("时长: ").append(gm.path("durationSeconds").asDouble(0)).append("秒\n");

                JsonNode timeline = root.path("auditoryTimeline");
                if (timeline.isArray() && !timeline.isEmpty()) {
                    sb.append("听觉氛围及画面建议:\n");
                    for (JsonNode slice : timeline) {
                        double start = slice.path("timeRange").path("start").asDouble(0);
                        double end = slice.path("timeRange").path("end").asDouble(0);
                        sb.append(String.format("- [%.1fs-%.1fs] 角色:%s 氛围:%s 节奏:%s 推荐画面:%s\n",
                                start, end,
                                slice.path("bestMatchedSlot").asText("unknown"),
                                slice.path("auditoryPerception").path("moodVibe").asText(""),
                                slice.path("auditoryPerception").path("perceivedPace").asText(""),
                                slice.path("recommendedMaterial").asText("")));
                    }
                }

                Map<String, Object> metadata = Map.of(
                        "audioId", audioId,
                        "audioName", root.path("audioName").asText(""),
                        "bpm", gm.path("bpm").asInt(120),
                        "overallStyle", gm.path("overallStyle").asText("unknown"),
                        "durationSeconds", gm.path("durationSeconds").asDouble(0),
                        "folderName", subDir.getName()
                );
                documents.add(new Document(audioId, sb.toString(), metadata));
            } catch (IOException e) {
                log.error("解析 BGM 描述文件失败: {}", jsonFile.getAbsolutePath(), e);
            }
        }

        if (!documents.isEmpty()) {
            bgmVectorStore.add(documents);
        }
        return "OK: 同步 " + documents.size() + " 个 BGM";
    }

    // ---- 品类知识向量同步 ----

    public String syncCategoryKnowledge() {
        log.info("开始同步 Category Knowledge 至 Chroma...");
        cleanupLegacyCategories();

        List<CategoryKnowledgeEntity> entities = categoryKnowledgeMapper.selectList(
                new LambdaQueryWrapper<CategoryKnowledgeEntity>().isNotNull(CategoryKnowledgeEntity::getCategoryId));
        if (entities == null || entities.isEmpty()) {
            return "OK: 无品类知识数据，跳过";
        }

        List<Document> documents = new ArrayList<>();
        for (CategoryKnowledgeEntity entity : entities) {
            try {
                if (entity.getCategoryId() == null || LEGACY_INVALID_CATEGORY_IDS.contains(entity.getCategoryId())) {
                    continue;
                }
                if (entity.getDynamicFields() == null || entity.getDynamicFields().isBlank()) {
                    continue;
                }
                Document doc = buildCategoryDocument(entity);
                if (doc != null) {
                    documents.add(doc);
                }
            } catch (Exception e) {
                log.error("解析品类知识失败: categoryId={}", entity.getCategoryId(), e);
            }
        }

        if (!documents.isEmpty()) {
            categoryVectorStore.add(documents);
        }
        return "OK: 同步 " + documents.size() + " 个品类";
    }

    private Document buildCategoryDocument(CategoryKnowledgeEntity entity) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("品类: ").append(entity.getCategoryName()).append("\n");
        if (entity.getSceneThreshold() != null) {
            sb.append("- scene_threshold: ").append(entity.getSceneThreshold()).append("\n");
        }

        JsonNode fields = buildKnowledgeFieldDefinitions(objectMapper.readTree(entity.getDynamicFields()));
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                sb.append("- ").append(field.path("fieldName").asText(""))
                        .append(" (").append(field.path("fieldType").asText("UNKNOWN")).append(")")
                        .append(": ").append(field.path("description").asText(""));
                JsonNode av = field.get("allowedValues");
                if (av != null && av.isArray() && !av.isEmpty()) {
                    sb.append("；allowedValues=").append(av);
                }
                sb.append("\n");
            }
        }

        Map<String, Object> metadata = Map.of(
                "categoryId", entity.getCategoryId(),
                "categoryName", entity.getCategoryName() != null ? entity.getCategoryName() : ""
        );
        return new Document(entity.getCategoryId(), sb.toString(), metadata);
    }

    // ---- helpers ----

    private Map<String, Object> safeSync(String label, java.util.function.Supplier<String> syncFn) {
        try {
            return Collections.singletonMap("result", syncFn.get());
        } catch (Exception e) {
            log.error("{} 同步失败", label, e);
            return Map.of("result", "FAILED: " + e.getMessage());
        }
    }

    private File findBgmDatabaseDir() {
        String baseDir = bgmVectorProperties.getAudioDatabaseDir();
        File f = new File(baseDir);
        if (f.exists() && f.isDirectory()) return f;
        f = new File("../" + baseDir);
        if (f.exists() && f.isDirectory()) return f;
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            Path p = Paths.get(userDir).resolve(baseDir);
            if (p.toFile().exists() && p.toFile().isDirectory()) return p.toFile();
            p = Paths.get(userDir).getParent().resolve(baseDir);
            if (p.toFile().exists() && p.toFile().isDirectory()) return p.toFile();
        }
        return null;
    }

    private void cleanupLegacyCategories() {
        for (String cid : LEGACY_INVALID_CATEGORY_IDS) {
            try { categoryVectorStore.delete(List.of(cid)); } catch (Exception ignored) { }
        }
    }

    private ArrayNode buildKnowledgeFieldDefinitions(JsonNode rawFields) {
        ArrayNode defs = objectMapper.createArrayNode();
        if (rawFields == null || !rawFields.isArray()) return defs;
        Set<String> visited = new HashSet<>();
        for (JsonNode field : rawFields) {
            if (!(field instanceof ObjectNode fObj)) continue;
            String name = normalizeFieldName(fObj.path("fieldName").asText(""));
            String type = normalizeFieldType(fObj.path("fieldType").asText(""), fObj.get("fieldValue"));
            if (name.isBlank() || type.isBlank() || !visited.add(name)) continue;
            ObjectNode def = objectMapper.createObjectNode();
            def.put("fieldName", name);
            def.put("fieldType", type);
            String desc = fObj.path("description").asText("").trim();
            if (!desc.isBlank()) def.put("description", desc);
            ArrayNode av = normalizeAllowedValues(fObj.get("allowedValues"));
            if ((av == null || av.isEmpty()) && fObj.get("fieldValue") != null && fObj.get("fieldValue").isArray()) {
                av = normalizeAllowedValues(fObj.get("fieldValue"));
            }
            if (av != null && !av.isEmpty()) def.set("allowedValues", av);
            defs.add(def);
        }
        return defs;
    }

    private String normalizeFieldName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_').replace(' ', '_')
                .toLowerCase().replaceAll("[^a-z0-9_]", "")
                .replaceAll("_+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
        if (s.isBlank()) return "";
        if (!Character.isLetter(s.charAt(0))) s = "f_" + s;
        return s;
    }

    private String normalizeFieldType(String raw, JsonNode fieldValue) {
        if (raw == null || raw.isBlank()) return "";
        String n = raw.trim().toUpperCase();
        if ("ARRAY".equals(n) || "MAP".equals(n)) return "JSON";
        if ("NUMBER".equals(n)) {
            if (fieldValue != null && !fieldValue.isNull() && fieldValue.isIntegralNumber()) return "INTEGER";
            return "DOUBLE";
        }
        return Set.of("STRING", "DOUBLE", "INTEGER", "BOOLEAN", "JSON").contains(n) ? n : "";
    }

    private ArrayNode normalizeAllowedValues(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) return null;
        ArrayNode arr = objectMapper.createArrayNode();
        Set<String> dedup = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) continue;
            if (dedup.add(item.toString())) arr.add(item);
        }
        return arr;
    }

    private void appendNonBlank(StringBuilder sb, String prefix, String value) {
        if (value != null && !value.isBlank()) sb.append(prefix).append(value);
    }

    private String joinArray(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) return "";
        List<String> vals = new ArrayList<>();
        for (JsonNode item : node) {
            String v = item.asText("");
            if (!v.isBlank()) vals.add(v);
        }
        return vals.isEmpty() ? "" : String.join(", ", vals);
    }
}
