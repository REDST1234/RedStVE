package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ArkProperties;
import com.bytedance.aivideo.creation.dto.SlotMatchLlmResult;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.service.SlotMatchLlmService;
import com.bytedance.aivideo.infrastructure.ark.ArkPayloadFactory;
import com.bytedance.aivideo.infrastructure.ark.ArkPromptTemplates;
import com.bytedance.aivideo.infrastructure.ark.ArkResponsesClient;
import com.bytedance.aivideo.infrastructure.ark.model.ArkResponseRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SlotMatchLlmServiceImpl implements SlotMatchLlmService {

    private static final Set<String> ALLOWED_MATCH_STATUS = Set.of("MATCHED", "PARTIAL", "MISSING", "VETOED");
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
    private static final Set<String> EXECUTABLE_STRATEGIES = Set.of(
            "TRIM_AND_CUT",
            "SMART_CROP",
            "LIGHTING_ADJUST",
            "LOCAL_BLUR",
            "LOOP_SEQUENCE",
            "KEN_BURNS_MOTION",
            "AUDIO_DUCKING"
    );
    private static final Set<String> ALLOWED_IMAGE_GEN_CATEGORIES = Set.of(
            "UI_ELEMENT", "STICKER", "LOGO", "ILLUSTRATION", "BACKGROUND", "NONE"
    );

    private final ArkPayloadFactory arkPayloadFactory;
    private final ArkResponsesClient arkResponsesClient;
    private final ArkProperties arkProperties;
    private final ObjectMapper objectMapper;

    public SlotMatchLlmServiceImpl(
            ArkPayloadFactory arkPayloadFactory,
            ArkResponsesClient arkResponsesClient,
            ArkProperties arkProperties,
            ObjectMapper objectMapper
    ) {
        this.arkPayloadFactory = arkPayloadFactory;
        this.arkResponsesClient = arkResponsesClient;
        this.arkProperties = arkProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SlotMatchLlmResult matchSlots(
            String projectId,
            String versionId,
            String templateSnapshotJson,
            List<CreativeMaterialEntity> materials
    ) {
        String materialProfilesJson = buildMaterialProfilesJson(materials);
        String prompt = String.format(
                ArkPromptTemplates.CREATION_SLOT_MATCH_JSON,
                projectId,
                versionId,
                templateSnapshotJson,
                materialProfilesJson
        );

        ArkResponseRequest request = new ArkResponseRequest();
        request.setModel(arkProperties.getModel());
        request.setMessages(arkPayloadFactory.buildTextInput(prompt));

        JsonNode response = arkResponsesClient.createResponse(request);
        String content = extractContent(response);
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "槽位匹配 LLM 返回空内容");
        }

        SlotMatchLlmResult result = parseAndValidate(projectId, versionId, stripMarkdownJsonFence(content), materials);
        log.info("slot match llm parsed: projectId={}, versionId={}, decisions={}, coverage={}",
                projectId, versionId, result.getMatchDecisions().size(), result.getOverallCoverage());
        return result;
    }

    private String buildMaterialProfilesJson(List<CreativeMaterialEntity> materials) {
        ArrayNode array = objectMapper.createArrayNode();
        for (CreativeMaterialEntity material : materials) {
            ObjectNode item = array.addObject();
            item.put("materialBizId", String.valueOf(material.getBizId()));
            item.put("materialType", material.getMaterialType());
            item.put("originalFileName", material.getOriginalFileName());
            item.put("duration", material.getDuration() == null ? 0.0 : material.getDuration());
            item.put("width", material.getWidth() == null ? 0 : material.getWidth());
            item.put("height", material.getHeight() == null ? 0 : material.getHeight());
            item.put("format", material.getFormat());
            if (material.getTextContent() != null && !material.getTextContent().isBlank()) {
                item.put("textContent", material.getTextContent());
            }
            try {
                if (material.getProfileJson() != null && !material.getProfileJson().isBlank()) {
                    item.set("profileJson", objectMapper.readTree(material.getProfileJson()));
                }
            } catch (Exception ex) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "素材画像 JSON 解析失败: " + material.getBizId());
            }
        }
        return array.toString();
    }

    private SlotMatchLlmResult parseAndValidate(
            String projectId,
            String versionId,
            String content,
            List<CreativeMaterialEntity> materials
    ) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode decisionsNode = root.path("matchDecisions");
            if (!decisionsNode.isArray() || decisionsNode.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "槽位匹配结果缺少 matchDecisions");
            }

            Set<String> materialIds = new HashSet<>();
            Map<String, CreativeMaterialEntity> materialMap = new HashMap<>();
            for (CreativeMaterialEntity material : materials) {
                String materialId = String.valueOf(material.getBizId());
                materialIds.add(materialId);
                materialMap.put(materialId, material);
            }

            SlotMatchLlmResult result = new SlotMatchLlmResult();
            result.setProjectId(root.path("projectId").asText(projectId));
            result.setVersionId(root.path("versionId").asText(versionId));
            result.setOverallCoverage(root.path("overallCoverage").asDouble(0.0));
            result.setGapSummary(root.path("gapSummary").asText(""));

            for (JsonNode decisionNode : decisionsNode) {
                SlotMatchLlmResult.Decision decision = new SlotMatchLlmResult.Decision();
                decision.setSegmentIndex(decisionNode.path("segmentIndex").asInt());
                decision.setSegmentRole(normalizeRole(decisionNode.path("segmentRole").asText("body")));
                decision.setMatchedAssetId(decisionNode.path("matchedAssetId").asText(""));
                decision.setMatchedHighlightId(decisionNode.path("matchedHighlightId").asText(""));
                decision.setMatchScore(clamp(decisionNode.path("matchScore").asDouble(0.0)));
                decision.setMatchStatus(normalizeMatchStatus(decisionNode.path("matchStatus").asText("MISSING")));
                decision.setVetoReason(decisionNode.path("vetoReason").asText(""));
                decision.setMatchReason(decisionNode.path("matchReason").asText(""));
                JsonNode adaptationPlan = decisionNode.path("adaptationPlan");
                if (adaptationPlan.isObject()) {
                    decision.setAdaptationPlan(adaptationPlan);
                } else {
                    ObjectNode emptyPlan = objectMapper.createObjectNode();
                    emptyPlan.putArray("strategyChain");
                    decision.setAdaptationPlan(emptyPlan);
                }

                // ---- Parse image generation eligibility (MISSING slots only) ----
                decision.setImageGenEligible(
                        decisionNode.has("imageGenEligible") && !decisionNode.path("imageGenEligible").isNull()
                                ? decisionNode.path("imageGenEligible").asBoolean(false) : false);
                if (Boolean.TRUE.equals(decision.getImageGenEligible())
                        && "MISSING".equals(decision.getMatchStatus())) {
                    decision.setImageGenCategory(
                            validateImageGenCategory(decisionNode.path("imageGenCategory").asText("NONE")));
                    decision.setImageGenPrompt(decisionNode.path("imageGenPrompt").asText(""));
                    decision.setImageGenDescription(decisionNode.path("imageGenDescription").asText(""));
                    if (decision.getImageGenPrompt().isBlank()) {
                        log.warn("imageGenEligible=true but imageGenPrompt is blank for segmentIndex={}",
                                decision.getSegmentIndex());
                        decision.setImageGenEligible(false);
                        decision.setImageGenCategory("NONE");
                    }
                }

                if (("MATCHED".equals(decision.getMatchStatus()) || "PARTIAL".equals(decision.getMatchStatus()))
                        && !materialIds.contains(decision.getMatchedAssetId())) {
                    throw new BizException(ErrorCode.INVALID_REQUEST, "槽位匹配返回了不存在的素材ID: " + decision.getMatchedAssetId());
                }
                validateStrategyChain(decision, materialMap.get(decision.getMatchedAssetId()));
                result.getMatchDecisions().add(decision);
            }
            return result;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "槽位匹配 LLM JSON 解析失败: " + ex.getMessage());
        }
    }

    private String validateImageGenCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NONE";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_IMAGE_GEN_CATEGORIES.contains(normalized) ? normalized : "NONE";
    }

    private void validateStrategyChain(SlotMatchLlmResult.Decision decision, CreativeMaterialEntity matchedMaterial) {
        JsonNode chain = decision.getAdaptationPlan().path("strategyChain");
        if (!chain.isArray()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "adaptationPlan.strategyChain 必须是数组");
        }
        if ("MISSING".equals(decision.getMatchStatus()) || "VETOED".equals(decision.getMatchStatus())) {
            if (chain.size() > 0) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "MISSING/VETOED 不允许返回可执行 strategyChain");
            }
            return;
        }
        if (isProtectedVisualAsset(matchedMaterial)) {
            if (chain.size() > 0) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "LOGO/插图/挂件图等保护类 IMAGE 素材不允许返回可执行 strategyChain");
            }
            return;
        }
        for (JsonNode strategy : chain) {
            String strategyType = strategy.path("strategyType").asText("").trim().toUpperCase(Locale.ROOT);
            if (!EXECUTABLE_STRATEGIES.contains(strategyType)) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "不支持的槽位适配策略: " + strategyType);
            }
            validateStrategyParams(strategyType, strategy.path("params"), matchedMaterial);
        }
    }

    private void validateStrategyParams(String strategyType, JsonNode params, CreativeMaterialEntity matchedMaterial) {
        if ("LIGHTING_ADJUST".equals(strategyType)) {
            if (matchedMaterial == null || !"VIDEO".equalsIgnoreCase(matchedMaterial.getMaterialType())) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "LIGHTING_ADJUST 仅允许用于 VIDEO 素材");
            }
            boolean hasBrightness = params.has("brightnessPercent") && params.path("brightnessPercent").isNumber();
            boolean hasContrast = params.has("contrastPercent") && params.path("contrastPercent").isNumber();
            if (!hasBrightness && !hasContrast) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "LIGHTING_ADJUST 至少需要一个有效的亮度或对比度参数");
            }
            if (matchedMaterial.getProfileJson() == null || !matchedMaterial.getProfileJson().contains("\"lighting\"")) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "LIGHTING_ADJUST 缺少 physicalAttributes.lighting 支撑");
            }
            return;
        }
        if ("LOCAL_BLUR".equals(strategyType)) {
            if (matchedMaterial == null || !"IMAGE".equalsIgnoreCase(matchedMaterial.getMaterialType())) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "LOCAL_BLUR 仅允许用于 IMAGE 素材");
            }
            JsonNode boundingBox = params.path("boundingBox");
            if (!boundingBox.isObject()
                    || !boundingBox.path("x").canConvertToInt()
                    || !boundingBox.path("y").canConvertToInt()
                    || !boundingBox.path("w").canConvertToInt()
                    || !boundingBox.path("h").canConvertToInt()
                    || boundingBox.path("w").asInt() <= 0
                    || boundingBox.path("h").asInt() <= 0) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "LOCAL_BLUR 必须携带有效的 boundingBox");
            }
            if (matchedMaterial.getProfileJson() == null || !matchedMaterial.getProfileJson().contains("\"spatialAnchor\"")) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "LOCAL_BLUR 缺少可复用的 spatialAnchor");
            }
        }
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null || message.get("content") == null) {
            return null;
        }
        return message.get("content").asText();
    }

    private String stripMarkdownJsonFence(String content) {
        String result = content == null ? "" : content.trim();
        if (result.startsWith("```json")) {
            result = result.substring(7).trim();
        } else if (result.startsWith("```")) {
            result = result.substring(3).trim();
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3).trim();
        }
        return result;
    }

    private String normalizeMatchStatus(String status) {
        String normalized = status == null ? "MISSING" : status.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_MATCH_STATUS.contains(normalized) ? normalized : "MISSING";
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "body";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if ("hook".equals(normalized) || "body".equals(normalized) || "climax".equals(normalized) || "outro".equals(normalized)) {
            return normalized;
        }
        return "body";
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private boolean isProtectedVisualAsset(CreativeMaterialEntity material) {
        if (material == null || !"IMAGE".equalsIgnoreCase(material.getMaterialType())) {
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
                Map.Entry<String, JsonNode> entry = fields.next();
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
}
