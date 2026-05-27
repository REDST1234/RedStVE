package com.bytedance.aivideo.deconstruct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructTemplateMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 全局模板资产服务。
 */
@Service
public class DeconstructTemplateService {

    private static final String TEMPLATE_STATUS_PUBLISHED = "PUBLISHED";

    private final DeconstructTemplateMapper deconstructTemplateMapper;
    private final ObjectMapper objectMapper;

    public DeconstructTemplateService(
            DeconstructTemplateMapper deconstructTemplateMapper,
            ObjectMapper objectMapper
    ) {
        this.deconstructTemplateMapper = deconstructTemplateMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 从拆解结果产出模板版本。
     * 规则：
     * 1) 同 taskId 使用稳定 templateId（tpl_{taskId-no-dash}）
     * 2) 内容哈希一致则复用最新版本，不重复创建
     * 3) 内容变化则递增版本
     */
    @Transactional(rollbackFor = Exception.class)
    public DeconstructTemplateEntity publishFromTask(String taskId, String templateJson) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        if (templateJson == null || templateJson.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "templateJson 不能为空");
        }

        String normalizedTaskId = taskId.trim();
        String templateId = "tpl_" + normalizedTaskId.replace("-", "");
        String snapshotHash = sha256(templateJson);

        DeconstructTemplateEntity latest = getLatestVersion(templateId);
        if (latest != null && snapshotHash.equals(latest.getSnapshotHash())) {
            return latest;
        }

        JsonNode root = parseTemplateJson(templateJson);
        String templateName = readText(root, "templateName", "模板-" + templateId);
        String categoryId = readText(root, "category", null);
        int nextVersion = latest == null ? 1 : latest.getTemplateVersion() + 1;

        DeconstructTemplateEntity entity = new DeconstructTemplateEntity();
        entity.setTemplateId(templateId);
        entity.setTemplateVersion(nextVersion);
        entity.setTemplateName(templateName);
        entity.setCategoryId(categoryId);
        entity.setStatus(TEMPLATE_STATUS_PUBLISHED);
        entity.setSourceTaskId(normalizedTaskId);
        entity.setTemplateJson(templateJson);
        entity.setSnapshotHash(snapshotHash);
        deconstructTemplateMapper.insert(entity);
        return entity;
    }

    public DeconstructTemplateEntity getTemplateVersion(String templateId, Integer version) {
        if (templateId == null || templateId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "templateId 不能为空");
        }
        LambdaQueryWrapper<DeconstructTemplateEntity> wrapper = new LambdaQueryWrapper<DeconstructTemplateEntity>()
                .eq(DeconstructTemplateEntity::getTemplateId, templateId.trim())
                .isNull(DeconstructTemplateEntity::getDeletedAt);
        if (version != null) {
            wrapper.eq(DeconstructTemplateEntity::getTemplateVersion, version);
        } else {
            wrapper.orderByDesc(DeconstructTemplateEntity::getTemplateVersion).last("LIMIT 1");
        }
        DeconstructTemplateEntity entity = deconstructTemplateMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BizException(ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在: " + templateId);
        }
        return entity;
    }

    public List<DeconstructTemplateEntity> listTemplates(String templateId) {
        LambdaQueryWrapper<DeconstructTemplateEntity> wrapper = new LambdaQueryWrapper<DeconstructTemplateEntity>()
                .isNull(DeconstructTemplateEntity::getDeletedAt)
                .orderByDesc(DeconstructTemplateEntity::getUpdatedAt);
        if (templateId != null && !templateId.isBlank()) {
            wrapper.eq(DeconstructTemplateEntity::getTemplateId, templateId.trim());
        }
        return deconstructTemplateMapper.selectList(wrapper);
    }

    private DeconstructTemplateEntity getLatestVersion(String templateId) {
        return deconstructTemplateMapper.selectOne(
                new LambdaQueryWrapper<DeconstructTemplateEntity>()
                        .eq(DeconstructTemplateEntity::getTemplateId, templateId)
                        .isNull(DeconstructTemplateEntity::getDeletedAt)
                        .orderByDesc(DeconstructTemplateEntity::getTemplateVersion)
                        .last("LIMIT 1")
        );
    }

    private JsonNode parseTemplateJson(String templateJson) {
        try {
            return objectMapper.readTree(templateJson);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "templateJson 非法: " + ex.getMessage());
        }
    }

    private String readText(JsonNode root, String fieldName, String defaultValue) {
        if (root == null || fieldName == null) {
            return defaultValue;
        }
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SHA-256 算法不可用: " + ex.getMessage());
        }
    }
}
