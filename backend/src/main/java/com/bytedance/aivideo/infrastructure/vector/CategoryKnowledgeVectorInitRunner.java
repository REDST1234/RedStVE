package com.bytedance.aivideo.infrastructure.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.video.entity.CategoryKnowledgeEntity;
import com.bytedance.aivideo.video.mapper.CategoryKnowledgeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

                JsonNode fields = objectMapper.readTree(entity.getDynamicFields());
                if (fields.isArray()) {
                    for (JsonNode field : fields) {
                        String name = field.has("fieldName") ? field.get("fieldName").asText() : "";
                        String type = field.has("fieldType") ? field.get("fieldType").asText("").toUpperCase() : "UNKNOWN";
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
}
