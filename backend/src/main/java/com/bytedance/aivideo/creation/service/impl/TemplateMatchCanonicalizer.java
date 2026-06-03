package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 结构匹配归一化器：
 * - 模板与素材的自然语言字段归一为固定 Tag
 * - 记录缺失字段与 fallback 映射计数
 */
final class TemplateMatchCanonicalizer {

    private static final String TAG_UNKNOWN = "UNKNOWN";
    private static final String TAG_STATIC = "STATIC";
    private static final String TAG_ZOOM_IN = "ZOOM_IN";
    private static final String TAG_ZOOM_OUT = "ZOOM_OUT";
    private static final String TAG_PAN = "PAN";

    private static final String TAG_CLOSE_UP = "CLOSE_UP";
    private static final String TAG_MID_SHOT = "MID_SHOT";
    private static final String TAG_WIDE_SHOT = "WIDE_SHOT";

    CanonicalTemplate canonicalizeTemplate(JsonNode rootNode) {
        JsonNode segmentsNode = rootNode.path("scriptStructure").path("segments");
        JsonNode shotsNode = rootNode.path("shots");
        JsonNode metaNode = rootNode.path("meta");

        List<CanonicalSegment> segments = new ArrayList<>();
        int templateMissingTagCount = 0;
        int fallbackMappingCount = 0;

        if (segmentsNode.isArray()) {
            for (JsonNode segmentNode : segmentsNode) {
                int segmentIndex = segmentNode.path("segmentIndex").asInt(0);
                String role = segmentNode.path("role").asText("");
                double minDuration = segmentNode.path("durationRange").path("min").asDouble(0.0);

                List<String> preferredMovementTags = readAllowedArray(segmentNode.path("preferredCameraMovements"), true);
                List<String> preferredShotTags = readAllowedArray(segmentNode.path("preferredShotTypes"), false);
                List<String> requiredVisualFunctions = readLowerCaseArray(segmentNode.path("requiredVisualFunctions"));

                String cameraMovementTag = preferredMovementTags.isEmpty() ? TAG_UNKNOWN : preferredMovementTags.get(0);
                String shotTypeTag = preferredShotTags.isEmpty() ? TAG_UNKNOWN : preferredShotTags.get(0);

                boolean movementResolvedFromSegment = !preferredMovementTags.isEmpty();
                boolean shotResolvedFromSegment = !preferredShotTags.isEmpty();
                if (shotsNode.isArray() && (!movementResolvedFromSegment || !shotResolvedFromSegment)) {
                    for (JsonNode shotNode : shotsNode) {
                        if (shotNode.path("belongsToSegment").asInt(-1) != segmentIndex) {
                            continue;
                        }
                        if (!movementResolvedFromSegment) {
                            TagValue movementTag = normalizeCameraMovementTag(
                                    shotNode.path("cameraMovementTag").asText(""),
                                    shotNode.path("cameraMovement").asText("")
                            );
                            cameraMovementTag = movementTag.tag();
                            if (movementTag.missing()) {
                                templateMissingTagCount++;
                            }
                            if (movementTag.fallbackUsed()) {
                                fallbackMappingCount++;
                            }
                        }
                        if (!shotResolvedFromSegment) {
                            TagValue shotTag = normalizeShotTypeTag(
                                    shotNode.path("shotTypeTag").asText(""),
                                    shotNode.path("shotType").asText("")
                            );
                            shotTypeTag = shotTag.tag();
                            if (shotTag.missing()) {
                                templateMissingTagCount++;
                            }
                            if (shotTag.fallbackUsed()) {
                                fallbackMappingCount++;
                            }
                        }
                        break;
                    }
                }

                if (TAG_UNKNOWN.equals(cameraMovementTag)) {
                    templateMissingTagCount++;
                }
                if (TAG_UNKNOWN.equals(shotTypeTag)) {
                    templateMissingTagCount++;
                }

                segments.add(new CanonicalSegment(
                        segmentIndex,
                        role,
                        minDuration,
                        cameraMovementTag,
                        shotTypeTag,
                        preferredMovementTags,
                        preferredShotTags,
                        requiredVisualFunctions
                ));
            }
        }

        String acousticEnvironment = metaNode.path("acousticEnvironment").asText("").toLowerCase(Locale.ROOT);
        String aspectRatio = metaNode.path("aspectRatio").asText("9:16");
        return new CanonicalTemplate(segments, acousticEnvironment, aspectRatio, templateMissingTagCount, fallbackMappingCount);
    }

    CanonicalMaterial canonicalizeMaterial(CreativeMaterialEntity material, JsonNode profileNode) {
        JsonNode semanticTagsNode = profileNode.path("semanticTags");
        JsonNode highlightsNode = profileNode.path("highlights");

        List<CanonicalHighlight> highlights = new ArrayList<>();
        int materialMissingTagCount = 0;
        int fallbackMappingCount = 0;

        if (highlightsNode != null && highlightsNode.isArray()) {
            for (JsonNode highlight : highlightsNode) {
                TagValue movementTag = normalizeCameraMovementTag(
                        highlight.path("cameraMovementTag").asText(""),
                        highlight.path("cameraMovement").asText("")
                );
                TagValue shotTag = normalizeShotTypeTag(
                        highlight.path("shotTypeTag").asText(""),
                        highlight.path("shotType").asText("")
                );
                if (movementTag.missing()) {
                    materialMissingTagCount++;
                }
                if (shotTag.missing()) {
                    materialMissingTagCount++;
                }
                if (movementTag.fallbackUsed()) {
                    fallbackMappingCount++;
                }
                if (shotTag.fallbackUsed()) {
                    fallbackMappingCount++;
                }

                highlights.add(new CanonicalHighlight(
                        movementTag.tag(),
                        shotTag.tag(),
                        highlight.path("actionState").asText(""),
                        highlight.path("textType").asText("")
                ));
            }
        }

        return new CanonicalMaterial(material, semanticTagsNode, highlights, materialMissingTagCount, fallbackMappingCount);
    }

    private TagValue normalizeCameraMovementTag(String rawTag, String rawText) {
        if (rawTag != null && !rawTag.isBlank()) {
            String normalized = rawTag.trim().toUpperCase(Locale.ROOT);
            if (TAG_STATIC.equals(normalized) || TAG_ZOOM_IN.equals(normalized)
                    || TAG_ZOOM_OUT.equals(normalized) || TAG_PAN.equals(normalized)) {
                return new TagValue(normalized, false, false);
            }
        }
        String text = rawText == null ? "" : rawText.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return new TagValue(TAG_UNKNOWN, true, false);
        }
        if (text.contains("static") || text.contains("固定") || text.contains("静止")) {
            return new TagValue(TAG_STATIC, false, true);
        }
        if (text.contains("zoom in") || text.contains("zoom_in") || text.contains("push") || text.contains("推近") || text.contains("推镜")) {
            return new TagValue(TAG_ZOOM_IN, false, true);
        }
        if (text.contains("zoom out") || text.contains("zoom_out") || text.contains("pull") || text.contains("拉远") || text.contains("拉镜")) {
            return new TagValue(TAG_ZOOM_OUT, false, true);
        }
        if (text.contains("pan") || text.contains("平移") || text.contains("摇镜") || text.contains("横移")) {
            return new TagValue(TAG_PAN, false, true);
        }
        return new TagValue(TAG_UNKNOWN, false, true);
    }

    private List<String> readAllowedArray(JsonNode node, boolean movement) {
        if (!node.isArray() || node.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String raw = item.asText("");
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if (movement) {
                if (TAG_STATIC.equals(normalized) || TAG_ZOOM_IN.equals(normalized)
                        || TAG_ZOOM_OUT.equals(normalized) || TAG_PAN.equals(normalized)
                        || TAG_UNKNOWN.equals(normalized)) {
                    values.add(normalized);
                }
            } else if (TAG_CLOSE_UP.equals(normalized) || TAG_MID_SHOT.equals(normalized)
                    || TAG_WIDE_SHOT.equals(normalized) || TAG_UNKNOWN.equals(normalized)) {
                values.add(normalized);
            }
        }
        return values.isEmpty() ? Collections.emptyList() : values;
    }

    private List<String> readLowerCaseArray(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String raw = item.asText("");
            if (raw == null || raw.isBlank()) {
                continue;
            }
            values.add(raw.trim().toLowerCase(Locale.ROOT));
        }
        return values.isEmpty() ? Collections.emptyList() : values;
    }

    private TagValue normalizeShotTypeTag(String rawTag, String rawText) {
        if (rawTag != null && !rawTag.isBlank()) {
            String normalized = rawTag.trim().toUpperCase(Locale.ROOT);
            if (TAG_CLOSE_UP.equals(normalized) || TAG_MID_SHOT.equals(normalized) || TAG_WIDE_SHOT.equals(normalized)) {
                return new TagValue(normalized, false, false);
            }
        }
        String text = rawText == null ? "" : rawText.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return new TagValue(TAG_UNKNOWN, true, false);
        }
        if (text.contains("close") || text.contains("特写") || text.contains("微距")) {
            return new TagValue(TAG_CLOSE_UP, false, true);
        }
        if (text.contains("mid") || text.contains("中景") || text.contains("半身")) {
            return new TagValue(TAG_MID_SHOT, false, true);
        }
        if (text.contains("wide") || text.contains("全景") || text.contains("远景")) {
            return new TagValue(TAG_WIDE_SHOT, false, true);
        }
        return new TagValue(TAG_UNKNOWN, false, true);
    }

    record CanonicalTemplate(
            List<CanonicalSegment> segments,
            String acousticEnvironment,
            String aspectRatio,
            int missingTagCount,
            int fallbackMappingCount
    ) {
    }

    record CanonicalSegment(
            int segmentIndex,
            String role,
            double minDuration,
            String cameraMovementTag,
            String shotTypeTag,
            List<String> preferredCameraMovements,
            List<String> preferredShotTypes,
            List<String> requiredVisualFunctions
    ) {
    }

    record CanonicalMaterial(
            CreativeMaterialEntity material,
            JsonNode semanticTagsNode,
            List<CanonicalHighlight> highlights,
            int missingTagCount,
            int fallbackMappingCount
    ) {
    }

    record CanonicalHighlight(
            String cameraMovementTag,
            String shotTypeTag,
            String actionState,
            String textType
    ) {
    }

    record TagValue(String tag, boolean missing, boolean fallbackUsed) {
    }
}
