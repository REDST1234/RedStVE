package com.bytedance.aivideo.infrastructure.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructTemplateMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 启动时自动将 PUBLISHED 状态的模板转换为自然语言文档，同步至 Chroma creation_template 集合。
 */
@Slf4j
@Component
public class TemplateVectorInitRunner implements CommandLineRunner {

    private final DeconstructTemplateMapper templateMapper;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public TemplateVectorInitRunner(
            DeconstructTemplateMapper templateMapper,
            @Qualifier("creationTemplateVectorStore") VectorStore vectorStore,
            ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("开始同步 PUBLISHED 模板至 Chroma creation_template VectorStore...");

        List<DeconstructTemplateEntity> entities = templateMapper.selectList(
                new LambdaQueryWrapper<DeconstructTemplateEntity>()
                        .eq(DeconstructTemplateEntity::getStatus, "PUBLISHED")
        );

        if (entities == null || entities.isEmpty()) {
            log.info("MySQL 中暂无 PUBLISHED 状态的模板，跳过同步。");
            return;
        }

        Map<String, List<DeconstructTemplateEntity>> grouped = entities.stream()
                .collect(Collectors.groupingBy(DeconstructTemplateEntity::getTemplateId));

        List<Document> documents = new ArrayList<>();
        List<String> idsToDelete = new ArrayList<>();

        for (Map.Entry<String, List<DeconstructTemplateEntity>> entry : grouped.entrySet()) {
            List<DeconstructTemplateEntity> versions = entry.getValue();

            // 收集所有历史版本以作删除清理
            for (DeconstructTemplateEntity v : versions) {
                idsToDelete.add("tpl_" + v.getTemplateId() + "_v" + v.getTemplateVersion());
            }

            // 选取最新版本
            DeconstructTemplateEntity latestEntity = versions.stream()
                    .max(Comparator.comparing(DeconstructTemplateEntity::getTemplateVersion))
                    .orElse(null);

            if (latestEntity == null) continue;

            try {
                if (latestEntity.getTemplateJson() == null || latestEntity.getTemplateJson().isBlank()) {
                    continue;
                }

                JsonNode rootNode = objectMapper.readTree(latestEntity.getTemplateJson());
                StringBuilder contentBuilder = new StringBuilder();
                
                contentBuilder.append("模板名: ").append(latestEntity.getTemplateName()).append("\n");
                contentBuilder.append("品类: ").append(latestEntity.getCategoryId()).append("\n");

                JsonNode metaNode = rootNode.path("meta");
                if (metaNode.has("description")) {
                    contentBuilder.append("说明: ").append(metaNode.get("description").asText()).append("\n");
                }
                if (metaNode.has("acousticEnvironment")) {
                    contentBuilder.append("声场环境(acousticEnvironment): ").append(metaNode.get("acousticEnvironment").asText()).append("\n");
                }
                if (metaNode.has("styles") && metaNode.get("styles").isArray()) {
                    contentBuilder.append("整体风格: ");
                    for (JsonNode style : metaNode.get("styles")) {
                        contentBuilder.append(style.asText()).append(" ");
                    }
                    contentBuilder.append("\n");
                }

                JsonNode scriptStructureNode = rootNode.path("scriptStructure");
                if (scriptStructureNode.has("segments") && scriptStructureNode.get("segments").isArray()) {
                    JsonNode segments = scriptStructureNode.get("segments");
                    contentBuilder.append("镜头数量: ").append(segments.size()).append("\n");
                    contentBuilder.append("镜头角色序列: ");
                    List<String> roles = new ArrayList<>();
                    for (JsonNode segment : segments) {
                        if (segment.has("role")) {
                            roles.add(segment.get("role").asText());
                        }
                    }
                    contentBuilder.append(String.join(" → ", roles)).append("\n");

                    contentBuilder.append("各镜头细节:\n");
                    for (JsonNode segment : segments) {
                        int index = segment.path("segmentIndex").asInt(0);
                        String role = segment.path("role").asText("unknown");
                        double minDur = segment.path("durationRange").path("min").asDouble(0.0);
                        double maxDur = segment.path("durationRange").path("max").asDouble(0.0);
                        contentBuilder.append(String.format("- 第%d段(%s): 时长%.1f-%.1f秒", index, role, minDur, maxDur));
                        if (segment.has("description")) {
                            contentBuilder.append(", 说明: ").append(segment.get("description").asText());
                        }
                        appendIfPresent(contentBuilder, ", 视觉镜头偏好: ", joinArray(segment.path("preferredShotTypes")));
                        appendIfPresent(contentBuilder, ", 运镜偏好: ", joinArray(segment.path("preferredCameraMovements")));
                        appendIfPresent(contentBuilder, ", 视觉功能需求: ", joinArray(segment.path("requiredVisualFunctions")));
                        appendIfPresent(contentBuilder, ", 字幕策略: ", segment.path("subtitleStrategy").asText(""));
                        appendIfPresent(contentBuilder, ", 包装密度: ", segment.path("packagingDensity").asText(""));
                        contentBuilder.append("\n");
                    }
                }

                JsonNode shotsNode = rootNode.path("shots");
                if (shotsNode.isArray() && !shotsNode.isEmpty()) {
                    List<String> shotTypeTags = new ArrayList<>();
                    List<String> movementTags = new ArrayList<>();
                    List<String> functionHints = new ArrayList<>();
                    for (JsonNode shot : shotsNode) {
                        String shotTypeTag = shot.path("shotTypeTag").asText("");
                        String movementTag = shot.path("cameraMovementTag").asText("");
                        String functionHint = shot.path("functionHint").asText("");
                        if (!shotTypeTag.isBlank()) {
                            shotTypeTags.add(shotTypeTag);
                        }
                        if (!movementTag.isBlank()) {
                            movementTags.add(movementTag);
                        }
                        if (!functionHint.isBlank()) {
                            functionHints.add(functionHint);
                        }
                    }
                    if (!shotTypeTags.isEmpty()) {
                        contentBuilder.append("代表镜头原型标签(shotTypeTag): ").append(String.join(", ", shotTypeTags)).append("\n");
                    }
                    if (!movementTags.isEmpty()) {
                        contentBuilder.append("代表镜头原型运镜(cameraMovementTag): ").append(String.join(", ", movementTags)).append("\n");
                    }
                    if (!functionHints.isEmpty()) {
                        contentBuilder.append("代表镜头原型功能(functionHint): ").append(String.join(", ", functionHints)).append("\n");
                    }
                }

                Map<String, Object> metadata = Map.of(
                        "templateId", latestEntity.getTemplateId(),
                        "templateVersion", latestEntity.getTemplateVersion(),
                        "categoryId", latestEntity.getCategoryId() != null ? latestEntity.getCategoryId() : "unknown",
                        "segmentCount", scriptStructureNode.path("totalSegments").asInt(0)
                );

                // 使用稳定 ID 确保同 ID 在 Chroma 会自动被 overwrite
                String documentId = "tpl_" + latestEntity.getTemplateId();
                Document doc = new Document(documentId, contentBuilder.toString(), metadata);
                documents.add(doc);
            } catch (Exception e) {
                log.error("同步模板失败，Template ID: {}", latestEntity.getTemplateId(), e);
            }
        }

        if (!idsToDelete.isEmpty()) {
            try {
                vectorStore.delete(idsToDelete);
                log.info("已向 Chroma 发送旧版本模板的删除指令，共 {} 个遗留 ID", idsToDelete.size());
            } catch (Exception e) {
                log.warn("清理旧版本模板异常，若 Chroma 无对应记录可忽略此警告: {}", e.getMessage());
            }
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("成功将 {} 个模板的特征语义刷入 Chroma creation_template VectorStore!", documents.size());
        }
    }

    private void appendIfPresent(StringBuilder contentBuilder, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            contentBuilder.append(prefix).append(value);
        }
    }

    private String joinArray(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? "" : String.join(", ", values);
    }
}
