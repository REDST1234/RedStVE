package com.bytedance.aivideo.engine.remotion;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ArkProperties;
import com.bytedance.aivideo.config.SfxAudioProperties;
import com.bytedance.aivideo.creation.dto.remotion.BgmConfig;
import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.creation.util.BgmMixLevelResolver;
import com.bytedance.aivideo.engine.remotion.dto.VideoOrchestrationResult;
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

import java.util.Iterator;
import java.util.Map;

@Slf4j
@Service
public class VideoOrchestrationService {

    private static final int DEFAULT_SCENE_DURATION_FRAMES = 90;
    private static final int DEFAULT_TRANSITION_DURATION_FRAMES = 15;

    private final ArkPayloadFactory arkPayloadFactory;
    private final ArkResponsesClient arkResponsesClient;
    private final ArkProperties arkProperties;
    private final SfxAudioProperties sfxAudioProperties;
    private final ObjectMapper objectMapper;

    public VideoOrchestrationService(
            ArkPayloadFactory arkPayloadFactory,
            ArkResponsesClient arkResponsesClient,
            ArkProperties arkProperties,
            SfxAudioProperties sfxAudioProperties,
            ObjectMapper objectMapper
    ) {
        this.arkPayloadFactory = arkPayloadFactory;
        this.arkResponsesClient = arkResponsesClient;
        this.arkProperties = arkProperties;
        this.sfxAudioProperties = sfxAudioProperties;
        this.objectMapper = objectMapper;
    }

    public CompositionScript orchestrateVideo(String projectDescription, String templateBrief, String assetBrief, String selectedBgmBrief, String canvasBrief, String versionStrategy) {
        return orchestrateVideoResult(projectDescription, templateBrief, assetBrief, selectedBgmBrief, canvasBrief, versionStrategy).getScript();
    }

    public CompositionScript sanitizeScript(CompositionScript script) {
        if (script == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.valueToTree(script);
            if (!(root instanceof ObjectNode objectNode)) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "Remotion 编排脚本必须是 JSON 对象");
            }
            normalizeDurationFieldAliases(objectNode);
            sanitizeCompositionTree(objectNode);
            CompositionScript sanitized = objectMapper.treeToValue(objectNode, CompositionScript.class);
            normalizeBgm(sanitized);
            validateScript(sanitized);
            return sanitized;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Remotion 编排脚本清洗失败: " + e.getMessage());
        }
    }

    /**
     * 基于用户描述和素材编排生成 CompositionScript，并保留原始模型输出。
     */
    public VideoOrchestrationResult orchestrateVideoResult(
            String projectDescription,
            String templateBrief,
            String assetBrief,
            String selectedBgmBrief,
            String canvasBrief,
            String versionStrategy
    ) {
        String styleInstruction = "";
        if ("fast_paced".equals(versionStrategy)) {
            styleInstruction = "【多版本生成策略：高频卡点版】要求：大幅缩短各文字层的持续时间(durationInFrames，建议30-60帧)，场景间强制使用 wipe 等快速转场，整体节奏极快，视觉冲击力强！将 bgm.mixLevel 强制写为 DRIVE。\n";
        } else if ("brand_quality".equals(versionStrategy)) {
            styleInstruction = "【多版本生成策略：品牌质感版】要求：减少字幕的密度，镜头停留时间适当延长，场景间多用 fade 淡入淡出转场，整体节奏舒缓、有高端质感！强制将 bgm.mixLevel 设置为 QUIET 或 BALANCED，强制配置 60 帧以上的 fadeInFrames（缓慢淡入），并在全局风格 globalStyle.fontTier 中偏向于使用 subtitle 或优雅的字体。\n";
        } else {
            styleInstruction = "【多版本生成策略：均衡原版】要求：保持原素材的自然节奏，信息传达与视觉呈现平衡。\n";
        }

        String promptText = String.format(
                ArkPromptTemplates.REMOTION_ORCHESTRATOR_JSON,
                projectDescription,
                templateBrief,
                assetBrief,
                selectedBgmBrief,
                canvasBrief
        );
        promptText = styleInstruction + "\n" + promptText;

        ArkResponseRequest request = new ArkResponseRequest();
        request.setModel(arkProperties.getModel());
        request.setMessages(arkPayloadFactory.buildTextInput(promptText));

        log.info("Sending remotion orchestration request to Ark LLM...");
        JsonNode response = arkResponsesClient.createResponse(request);
        String responseContent = extractContent(response);
        if (responseContent == null || responseContent.isBlank()) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "Remotion 编排 LLM 返回空内容");
        }

        CompositionScript script = parseAndCleanJson(responseContent);

        VideoOrchestrationResult result = new VideoOrchestrationResult();
        result.setScript(script);
        result.setRawContent(stripMarkdownJsonFence(responseContent));
        result.setReasoningContent(extractReasoningContent(response));
        return result;
    }

    /**
     * 清理 LLM 输出的 markdown 格式并反序列化为 CompositionScript
     */
    private CompositionScript parseAndCleanJson(String rawResponse) {
        try {
            String jsonStr = stripMarkdownJsonFence(rawResponse);
            JsonNode root = objectMapper.readTree(jsonStr);
            if (root == null || !root.isObject()) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "Remotion 编排结果必须是 JSON 对象");
            }

            normalizeDurationFieldAliases(root);
            sanitizeCompositionTree((ObjectNode) root);

            CompositionScript script = objectMapper.treeToValue(root, CompositionScript.class);
            validateScript(script);
            return script;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse LLM response into CompositionScript: \n{}", rawResponse, e);
            throw new RuntimeException("Orchestration JSON parsing failed", e);
        }
    }

    private void normalizeDurationFieldAliases(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            if (!objectNode.has("durationInFrames") && objectNode.has("durationFrames")) {
                objectNode.set("durationInFrames", objectNode.get("durationFrames"));
                objectNode.remove("durationFrames");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                normalizeDurationFieldAliases(entry.getValue());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                normalizeDurationFieldAliases(child);
            }
        }
    }

    private void sanitizeCompositionTree(ObjectNode root) {
        removeNullField(root, "globalStyle");
        sanitizeScenes(root);
        sanitizeTransitions(root);
        sanitizeBgm(root);
    }

    private void sanitizeScenes(ObjectNode root) {
        JsonNode scenesNode = root.get("scenes");
        if (!(scenesNode instanceof ArrayNode scenesArray)) {
            return;
        }
        ArrayNode sanitizedScenes = objectMapper.createArrayNode();
        for (JsonNode sceneNode : scenesArray) {
            if (!(sceneNode instanceof ObjectNode sceneObject)) {
                continue;
            }
            removeNullField(sceneObject, "role");
            JsonNode layersNode = sceneObject.get("layers");
            ArrayNode layers = sanitizeLayers(layersNode instanceof ArrayNode arrayNode ? arrayNode : objectMapper.createArrayNode());
            sceneObject.set("layers", layers);
            if (!sceneObject.has("durationInFrames") || sceneObject.path("durationInFrames").asInt(0) <= 0) {
                sceneObject.put("durationInFrames", inferSceneDuration(layers));
            }
            sanitizedScenes.add(sceneObject);
        }
        root.set("scenes", sanitizedScenes);
    }

    private ArrayNode sanitizeLayers(ArrayNode layersArray) {
        ArrayNode sanitizedLayers = objectMapper.createArrayNode();
        for (JsonNode layerNode : layersArray) {
            if (!(layerNode instanceof ObjectNode layerObject)) {
                continue;
            }
            removeNullField(layerObject, "durationInFrames");
            if (!layerObject.has("params") || layerObject.get("params") == null || layerObject.get("params").isNull()) {
                layerObject.set("params", objectMapper.createObjectNode());
            }
            if (!layerObject.has("enterAtFrame") || layerObject.path("enterAtFrame").asInt(-1) < 0) {
                layerObject.put("enterAtFrame", 0);
            }
            // 为缺少 src 的 media.audio 层根据 cueType 自动填入默认音频 URL
            injectSfxAudioSrc(layerObject);
            sanitizedLayers.add(layerObject);
        }
        return sanitizedLayers;
    }

    /**
     * 若 layer 是 media.audio 且缺少 src 但有 cueType，则从 SFX 映射表自动填入 src。
     */
    private void injectSfxAudioSrc(ObjectNode layerObject) {
        String preset = layerObject.path("preset").asText("");
        if (!"media.audio".equals(preset)) {
            return;
        }
        ObjectNode params = (ObjectNode) layerObject.get("params");
        if (params == null) {
            return;
        }
        // 已有 src 则不覆盖
        if (params.has("src") && !params.path("src").asText("").isBlank()) {
            return;
        }
        String cueType = params.path("cueType").asText("");
        if (cueType.isBlank()) {
            return;
        }
        String resolvedSrc = sfxAudioProperties.resolveSrc(cueType);
        if (resolvedSrc != null && !resolvedSrc.isBlank()) {
            params.put("src", resolvedSrc);
            log.info("SFX audio src auto-injected: cueType={}, src={}", cueType, resolvedSrc);
        }
    }

    private int inferSceneDuration(ArrayNode layers) {
        int maxDuration = 0;
        for (JsonNode layerNode : layers) {
            int enterAt = layerNode.path("enterAtFrame").asInt(0);
            int duration = layerNode.path("durationInFrames").asInt(0);
            maxDuration = Math.max(maxDuration, enterAt + duration);
        }
        return maxDuration > 0 ? maxDuration : DEFAULT_SCENE_DURATION_FRAMES;
    }

    private void sanitizeTransitions(ObjectNode root) {
        JsonNode transitionsNode = root.get("transitions");
        if (transitionsNode == null || transitionsNode.isNull()) {
            root.set("transitions", objectMapper.createArrayNode());
            return;
        }
        if (!(transitionsNode instanceof ArrayNode transitionsArray)) {
            root.set("transitions", objectMapper.createArrayNode());
            return;
        }
        ArrayNode sanitizedTransitions = objectMapper.createArrayNode();
        for (JsonNode transitionNode : transitionsArray) {
            if (!(transitionNode instanceof ObjectNode transitionObject)) {
                continue;
            }
            ObjectNode params = ensureObject(transitionObject, "params");
            if (!params.has("durationInFrames") || params.path("durationInFrames").asInt(0) <= 0) {
                params.put("durationInFrames", DEFAULT_TRANSITION_DURATION_FRAMES);
            }
            if (!params.has("timing") || params.path("timing").asText("").isBlank()) {
                params.put("timing", "linear");
            }
            JsonNode overlayNode = transitionObject.get("overlay");
            if (overlayNode instanceof ObjectNode overlayObject) {
                ensureObject(overlayObject, "params");
            } else if (overlayNode == null || overlayNode.isNull()) {
                transitionObject.remove("overlay");
            }
            sanitizedTransitions.add(transitionObject);
        }
        root.set("transitions", sanitizedTransitions);
    }

    private void sanitizeBgm(ObjectNode root) {
        JsonNode bgmNode = root.get("bgm");
        if (!(bgmNode instanceof ObjectNode bgmObject)) {
            return;
        }
        if (bgmObject.has("volume")) {
            bgmObject.remove("volume");
        }
    }

    private ObjectNode ensureObject(ObjectNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode objectNode = objectMapper.createObjectNode();
        parent.set(fieldName, objectNode);
        return objectNode;
    }

    private void removeNullField(ObjectNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || value.isNull()) {
            parent.remove(fieldName);
        }
    }

    private void validateScript(CompositionScript script) {
        if (script == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Remotion 编排结果为空");
        }
        if (script.getScenes() == null || script.getScenes().isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Remotion 编排结果缺少 scenes");
        }
        for (int i = 0; i < script.getScenes().size(); i++) {
            var scene = script.getScenes().get(i);
            if (scene == null) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "Remotion 编排结果包含空 scene，索引: " + i);
            }
            if (scene.getDurationInFrames() == null || scene.getDurationInFrames() <= 0) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "scene.durationInFrames 非法，索引: " + i);
            }
            if (scene.getLayers() == null) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "scene.layers 不能为空，索引: " + i);
            }
            for (int j = 0; j < scene.getLayers().size(); j++) {
                var layer = scene.getLayers().get(j);
                if (layer == null) {
                    throw new BizException(ErrorCode.INVALID_REQUEST, "scene.layers 包含空 layer，scene索引: " + i + ", layer索引: " + j);
                }
                if (layer.getPreset() == null || layer.getPreset().isBlank()) {
                    throw new BizException(ErrorCode.INVALID_REQUEST, "layer.preset 不能为空，scene索引: " + i + ", layer索引: " + j);
                }
                if (layer.getParams() == null) {
                    throw new BizException(ErrorCode.INVALID_REQUEST, "layer.params 必须是对象，scene索引: " + i + ", layer索引: " + j);
                }
                // 校验 media / motion 类 preset 必须有 src
                // 例外: media.audio 若有 cueType，src 可由后端 SFX 映射自动填入
                String preset = layer.getPreset();
                if (preset != null && (preset.startsWith("media.") || preset.startsWith("motion."))) {
                    boolean isAudioWithCueType = "media.audio".equals(preset)
                            && layer.getParams().get("cueType") != null
                            && !((String) layer.getParams().get("cueType")).isBlank();
                    if (!isAudioWithCueType) {
                        Object src = layer.getParams().get("src");
                        if (src == null || (src instanceof String s && s.isBlank())) {
                            throw new BizException(ErrorCode.INVALID_REQUEST,
                                    "layer.preset=" + preset + " 缺少必填字段 params.src，scene索引: " + i + ", layerId: " + layer.getLayerId());
                        }
                    }
                }
            }
        }
        if (script.getTransitions() != null) {
            for (int i = 0; i < script.getTransitions().size(); i++) {
                var transition = script.getTransitions().get(i);
                if (transition == null) {
                    throw new BizException(ErrorCode.INVALID_REQUEST, "transitions 包含空项，索引: " + i);
                }
                if (transition.getParams() == null) {
                    throw new BizException(ErrorCode.INVALID_REQUEST, "transition.params 必须是对象，索引: " + i);
                }
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

    private String extractReasoningContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null || message.get("reasoning_content") == null || message.get("reasoning_content").isNull()) {
            return null;
        }
        return message.get("reasoning_content").asText();
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

    private void normalizeBgm(CompositionScript script) {
        if (script == null || script.getBgm() == null) {
            return;
        }
        BgmConfig bgm = script.getBgm();
        String mixLevel = normalizeMixLevel(bgm.getMixLevel());
        if (mixLevel != null) {
            bgm.setMixLevel(mixLevel);
            bgm.setVolume(BgmMixLevelResolver.resolveVolume(mixLevel));
            return;
        }

        Double volume = bgm.getVolume();
        if (volume == null) {
            bgm.setMixLevel("BALANCED");
            bgm.setVolume(BgmMixLevelResolver.resolveVolume("BALANCED"));
            return;
        }
        bgm.setVolume(clamp(volume, 0.0, 1.0));
    }

    private String normalizeMixLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = BgmMixLevelResolver.normalize(raw);
        if (!normalized.equalsIgnoreCase(raw.trim())) {
            log.warn("Unknown or non-canonical bgm mixLevel from orchestration result: {}. Normalized to {}.", raw, normalized);
        }
        return normalized;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
