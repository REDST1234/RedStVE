package com.bytedance.aivideo.infrastructure.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.video.entity.CategoryKnowledgeEntity;
import com.bytedance.aivideo.video.mapper.CategoryKnowledgeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动时自动将 MySQL 中的 category_knowledge 表数据全量同步至 Chroma 向量库中。
 * 由于 Chroma 的 add 操作是以 ID 为主键的 upsert，因此具有幂等性，重复执行不会有副作用。
 */
@Slf4j
@Component
public class CategoryKnowledgeVectorInitRunner implements CommandLineRunner {
    /**
     * 历史错误细粒度品类ID（应归并到宏观类），启动时从向量库清理并禁止再次同步。
     */
    private static final Set<String> LEGACY_INVALID_CATEGORY_IDS = Set.of(
            "school_canteen_short_drama",
            "school_canteen_drama",
            "car_review"
    );

    private final CategoryKnowledgeMapper categoryKnowledgeMapper;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public CategoryKnowledgeVectorInitRunner(CategoryKnowledgeMapper categoryKnowledgeMapper,
                                             VectorStore vectorStore,
                                             ObjectMapper objectMapper) {
        this.categoryKnowledgeMapper = categoryKnowledgeMapper;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("开始同步 Category Knowledge MySQL 数据至 Chroma VectorStore...");
        cleanupLegacyCategoriesFromVectorStore();

        List<CategoryKnowledgeEntity> entities = categoryKnowledgeMapper.selectList(
                new LambdaQueryWrapper<CategoryKnowledgeEntity>().isNotNull(CategoryKnowledgeEntity::getCategoryId)
        );

        if (entities == null || entities.isEmpty()) {
            log.info("MySQL 中暂无品类知识库数据，跳过同步。");
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (CategoryKnowledgeEntity entity : entities) {
            try {
                if (entity.getCategoryId() == null || LEGACY_INVALID_CATEGORY_IDS.contains(entity.getCategoryId())) {
                    log.info("跳过历史细粒度品类同步: categoryId={}", entity.getCategoryId());
                    continue;
                }
                if (entity.getDynamicFields() == null || entity.getDynamicFields().isBlank()) {
                    continue;
                }

                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("品类: ").append(entity.getCategoryName()).append("\n");
                if (entity.getSceneThreshold() != null) {
                    contentBuilder.append("- scene_threshold (DOUBLE): 运行时视频拆解可参考的镜头切分阈值 hint = ")
                            .append(entity.getSceneThreshold()).append("\n");
                }

                JsonNode fields = buildKnowledgeFieldDefinitions(objectMapper.readTree(entity.getDynamicFields()));
                if (fields.isArray()) {
                    for (JsonNode field : fields) {
                        String name = field.has("fieldName") ? field.get("fieldName").asText() : "";
                        String type = field.has("fieldType") ? field.get("fieldType").asText("").toUpperCase() : "UNKNOWN";
                        String desc = field.has("description") ? field.get("description").asText() : "";
                        if (!name.isBlank()) {
                            contentBuilder.append("- ").append(name)
                                    .append(" (").append(type).append(")")
                                    .append(": ").append(desc);
                            JsonNode allowedValues = field.get("allowedValues");
                            if (allowedValues != null && allowedValues.isArray() && !allowedValues.isEmpty()) {
                                contentBuilder.append("；allowedValues=").append(allowedValues.toString());
                            }
                            contentBuilder.append("\n");
                        }
                    }
                }

                Map<String, Object> metadata = Map.of(
                        "categoryId", entity.getCategoryId(),
                        "categoryName", entity.getCategoryName() != null ? entity.getCategoryName() : ""
                );

                Document doc = new Document(entity.getCategoryId(), contentBuilder.toString(), metadata);
                documents.add(doc);
            } catch (Exception e) {
                log.error("同步解析失败，Category ID: {}", entity.getCategoryId(), e);
            }
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("成功将 {} 个品类模板的特征语义刷入 Chroma VectorStore!", documents.size());
        }
    }

    private void cleanupLegacyCategoriesFromVectorStore() {
        for (String invalidCategoryId : LEGACY_INVALID_CATEGORY_IDS) {
            try {
                vectorStore.delete(List.of(invalidCategoryId));
                log.info("已从 Chroma 删除历史细粒度品类: categoryId={}", invalidCategoryId);
            } catch (Exception ex) {
                log.warn("删除 Chroma 历史细粒度品类失败: categoryId={}", invalidCategoryId, ex);
            }
        }
    }

    private ArrayNode buildKnowledgeFieldDefinitions(JsonNode rawFields) {
        ArrayNode definitions = objectMapper.createArrayNode();
        if (rawFields == null || !rawFields.isArray()) {
            return definitions;
        }
        Set<String> visited = new HashSet<>();
        for (JsonNode field : rawFields) {
            if (!(field instanceof ObjectNode fieldObj)) {
                continue;
            }
            String fieldName = normalizeFieldName(fieldObj.path("fieldName").asText(""));
            String fieldType = normalizeFieldType(fieldObj.path("fieldType").asText(""), fieldObj.get("fieldValue"));
            if (fieldName.isBlank() || fieldType.isBlank() || !visited.add(fieldName)) {
                continue;
            }
            ObjectNode definition = objectMapper.createObjectNode();
            definition.put("fieldName", fieldName);
            definition.put("fieldType", fieldType);
            String description = normalizeDescription(fieldObj.path("description").asText(""));
            if (!description.isBlank()) {
                definition.put("description", description);
            }
            ArrayNode allowedValues = normalizeAllowedValues(fieldObj.get("allowedValues"));
            if ((allowedValues == null || allowedValues.isEmpty()) && fieldObj.get("fieldValue") != null && fieldObj.get("fieldValue").isArray()) {
                allowedValues = normalizeAllowedValues(fieldObj.get("fieldValue"));
            }
            if (allowedValues != null && !allowedValues.isEmpty()) {
                definition.set("allowedValues", allowedValues);
            }
            definitions.add(definition);
        }
        return definitions;
    }

    private String normalizeFieldName(String rawFieldName) {
        if (rawFieldName == null || rawFieldName.isBlank()) {
            return "";
        }
        String trimmed = rawFieldName.trim();
        String snakeCase = trimmed
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .replace(' ', '_')
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "")
                .replaceAll("_+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        if (snakeCase.isBlank()) {
            return "";
        }
        if (!Character.isLetter(snakeCase.charAt(0))) {
            snakeCase = "f_" + snakeCase;
        }
        return snakeCase;
    }

    private String normalizeDescription(String rawDescription) {
        return rawDescription == null ? "" : rawDescription.trim();
    }

    private String normalizeFieldType(String rawFieldType, JsonNode fieldValue) {
        if (rawFieldType == null || rawFieldType.isBlank()) {
            return "";
        }
        String normalized = rawFieldType.trim().toUpperCase();
        if ("ARRAY".equals(normalized) || "MAP".equals(normalized)) {
            return "JSON";
        }
        if ("NUMBER".equals(normalized)) {
            return isIntegralNumber(fieldValue) ? "INTEGER" : "DOUBLE";
        }
        return Set.of("STRING", "DOUBLE", "INTEGER", "BOOLEAN", "JSON").contains(normalized) ? normalized : "";
    }

    private boolean isIntegralNumber(JsonNode fieldValue) {
        if (fieldValue == null || fieldValue.isNull()) {
            return false;
        }
        if (fieldValue.isIntegralNumber()) {
            return true;
        }
        if (!fieldValue.isNumber()) {
            return false;
        }
        double value = fieldValue.asDouble();
        return Math.rint(value) == value;
    }

    private ArrayNode normalizeAllowedValues(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return null;
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        Set<String> dedup = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            String key = item.toString();
            if (dedup.add(key)) {
                normalized.add(item);
            }
        }
        return normalized;
    }
}
