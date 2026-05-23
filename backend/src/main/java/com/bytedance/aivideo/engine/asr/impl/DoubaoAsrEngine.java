package com.bytedance.aivideo.engine.asr.impl;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ArkProperties;
import com.bytedance.aivideo.config.AsrProperties;
import com.bytedance.aivideo.engine.asr.api.AsrEngine;
import com.bytedance.aivideo.engine.asr.model.AsrSegmentResult;
import com.bytedance.aivideo.engine.asr.model.AsrTranscriptionResult;
import com.bytedance.aivideo.infrastructure.ark.ArkPayloadFactory;
import com.bytedance.aivideo.infrastructure.ark.ArkPromptTemplates;
import com.bytedance.aivideo.infrastructure.ark.ArkResponsesClient;
import com.bytedance.aivideo.infrastructure.ark.model.ArkResponseRequest;
import com.bytedance.aivideo.infrastructure.ark.model.ArkThinking;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 基于豆包 chat/completions 协议的 ASR 引擎实现。
 */
@Component
@Slf4j
public class DoubaoAsrEngine implements AsrEngine {

    private static final String AUDIO_FORMAT_MP3 = "mp3";

    private final ArkResponsesClient arkResponsesClient;
    private final ArkPayloadFactory arkPayloadFactory;
    private final AsrProperties asrProperties;
    private final ArkProperties arkProperties;
    private final ObjectMapper objectMapper;

    public DoubaoAsrEngine(
            ArkResponsesClient arkResponsesClient,
            ArkPayloadFactory arkPayloadFactory,
            AsrProperties asrProperties,
            ArkProperties arkProperties,
            ObjectMapper objectMapper
    ) {
        this.arkResponsesClient = arkResponsesClient;
        this.arkPayloadFactory = arkPayloadFactory;
        this.asrProperties = asrProperties;
        this.arkProperties = arkProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AsrTranscriptionResult transcribe(Path audioFile, String taskId) {
        if (audioFile == null) {
            throw new BizException(ErrorCode.ASR_ERROR, "音频文件路径不能为空");
        }
        if (!Files.exists(audioFile)) {
            throw new BizException(ErrorCode.ASR_ERROR, "音频文件不存在: " + audioFile);
        }
        byte[] audioBytes;
        try {
            audioBytes = Files.readAllBytes(audioFile);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.ASR_ERROR, "读取音频文件失败: " + ex.getMessage());
        }
        if (audioBytes.length == 0) {
            throw new BizException(ErrorCode.ASR_ERROR, "音频文件为空");
        }
        if (audioBytes.length > asrProperties.getMaxAudioBytes()) {
            throw new BizException(
                    ErrorCode.ASR_ERROR,
                    "音频文件超过限制: " + audioBytes.length + " > " + asrProperties.getMaxAudioBytes()
            );
        }

        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
        String prompt = ArkPromptTemplates.ASR_TRANSCRIBE_JSON;
        int maxAttempts = Math.max(1, arkProperties.getMaxRetries() + 1);
        BizException lastBizException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String attemptPrompt = attempt == 1 ? prompt : strictJsonPrompt(prompt);
            try {
                ArkResponseRequest request = new ArkResponseRequest();
                request.setModel(asrProperties.getDoubao().getModel());
                request.setMessages(arkPayloadFactory.buildAudioInput(base64Audio, AUDIO_FORMAT_MP3, attemptPrompt));

                ArkThinking thinking = new ArkThinking();
                thinking.setType("disabled");
                request.setThinking(thinking);

                log.info("doubao asr request start: taskId={}, attempt={}, model={}", taskId, attempt, request.getModel());

                JsonNode responseNode = arkResponsesClient.createResponse(request);
                AsrTranscriptionResult result = parseAsrResultFromResponse(responseNode);
                validateResult(result);
                log.info("doubao asr request finished: taskId={}, attempt={}, segmentCount={}",
                        taskId, attempt, result.getSegments().size());
                return result;
            } catch (BizException ex) {
                lastBizException = ex;
                log.warn("doubao asr parse failed: taskId={}, attempt={}, reason={}", taskId, attempt, ex.getMessage());
            }
        }
        throw lastBizException == null
                ? new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 响应解析失败")
                : lastBizException;
    }

    private String strictJsonPrompt(String originalPrompt) {
        return originalPrompt + "\n" + ArkPromptTemplates.STRICT_JSON_SUFFIX;
    }

    private AsrTranscriptionResult parseAsrResultFromResponse(JsonNode responseNode) {
        if (responseNode == null || responseNode.isNull()) {
            throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 响应为空");
        }

        List<String> candidates = collectOutputCandidates(responseNode);
        if (candidates.isEmpty()) {
            throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 响应中未找到可解析文本");
        }

        List<AsrTranscriptionResult> parsedResults = new ArrayList<>();
        for (String candidate : candidates) {
            try {
                parsedResults.add(parseAsrResult(candidate));
            } catch (BizException ex) {
                log.warn("Candidate parse failed: {}", ex.getMessage());
            }
        }
        if (parsedResults.isEmpty()) {
            throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 响应中未找到可解析 JSON");
        }
        if (parsedResults.size() == 1) {
            return parsedResults.get(0);
        }
        return mergeAsrResults(parsedResults);
    }

    private List<String> collectOutputCandidates(JsonNode responseNode) {
        Set<String> candidates = new LinkedHashSet<>();
        String outputText = responseNode.path("output_text").asText(null);
        if (outputText != null && !outputText.isBlank()) {
            candidates.add(outputText);
        }

        JsonNode outputNode = responseNode.path("output");
        if (outputNode.isArray()) {
            for (JsonNode outputItem : outputNode) {
                JsonNode contentNode = outputItem.path("content");
                if (!contentNode.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : contentNode) {
                    String text = contentItem.path("text").asText(null);
                    if (text != null && !text.isBlank()) {
                        candidates.add(text);
                    }
                }
            }
        }

        JsonNode choicesNode = responseNode.path("choices");
        if (choicesNode.isArray()) {
            for (JsonNode choiceNode : choicesNode) {
                JsonNode contentNode = choiceNode.path("message").path("content");
                if (contentNode.isTextual() && !contentNode.asText().isBlank()) {
                    candidates.add(contentNode.asText());
                    continue;
                }
                if (!contentNode.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : contentNode) {
                    if (contentItem.isTextual() && !contentItem.asText().isBlank()) {
                        candidates.add(contentItem.asText());
                        continue;
                    }
                    String text = contentItem.path("text").asText(null);
                    if (text != null && !text.isBlank()) {
                        candidates.add(text);
                    }
                }
            }
        }

        List<String> collectAll = new ArrayList<>();
        collectTextFields(responseNode, collectAll);
        for (String text : collectAll) {
            if (text != null && !text.isBlank()) {
                candidates.add(text);
            }
        }
        return new ArrayList<>(candidates);
    }

    private void collectTextFields(JsonNode node, List<String> collector) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                JsonNode child = node.get(field);
                if ("text".equals(field) && child != null && child.isTextual() && !child.asText().isBlank()) {
                    collector.add(child.asText());
                }
                collectTextFields(child, collector);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectTextFields(item, collector);
            }
        }
    }

    private AsrTranscriptionResult parseAsrResult(String outputText) {
        if (outputText == null || outputText.isBlank()) {
            throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 输出文本为空");
        }
        String normalized = normalizeJsonText(outputText);
        try {
            JsonNode root = objectMapper.readTree(normalized);
            AsrTranscriptionResult result = new AsrTranscriptionResult();
            String fullText = firstNonBlank(
                    root.path("fullText").asText(null),
                    root.path("full_text").asText(null),
                    root.path("text").asText(null),
                    root.path("transcript").asText(null)
            );
            result.setFullText(fullText);

            JsonNode segmentsNode = root.path("segments");
            if (!segmentsNode.isArray()) {
                throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR segments 缺失或非数组");
            }

            int index = 0;
            for (JsonNode segmentNode : segmentsNode) {
                AsrSegmentResult segment = parseSegment(segmentNode, index);
                result.getSegments().add(segment);
                index++;
            }
            return result;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 输出 JSON 解析失败: " + ex.getMessage());
        }
    }

    private AsrSegmentResult parseSegment(JsonNode segmentNode, int fallbackIndex) {
        AsrSegmentResult segment = new AsrSegmentResult();
        segment.setSegmentIndex(segmentNode.path("segmentIndex").isInt()
                ? segmentNode.path("segmentIndex").asInt()
                : fallbackIndex);
        segment.setStartSec(parseNumberField(segmentNode, "start", "startTime", "start_sec"));
        segment.setEndSec(parseNumberField(segmentNode, "end", "endTime", "end_sec"));
        segment.setText(firstNonBlank(
                segmentNode.path("text").asText(null),
                segmentNode.path("sentence").asText(null),
                segmentNode.path("content").asText(null)
        ));
        segment.setConfidence(parseNumberField(segmentNode, "confidence", "score"));
        segment.setSpeakerLabel(firstNonBlank(
                segmentNode.path("speaker").asText(null),
                segmentNode.path("speakerLabel").asText(null),
                segmentNode.path("speaker_label").asText(null),
                segmentNode.path("speaker_id").asText(null)
        ));
        return segment;
    }

    private AsrTranscriptionResult mergeAsrResults(List<AsrTranscriptionResult> results) {
        AsrTranscriptionResult merged = new AsrTranscriptionResult();
        Set<String> dedup = new HashSet<>();
        List<AsrSegmentResult> mergedSegments = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();
        Set<String> fullTextParts = new LinkedHashSet<>();

        for (AsrTranscriptionResult result : results) {
            if (result.getFullText() != null && !result.getFullText().isBlank()) {
                fullTextParts.add(result.getFullText().trim());
            }
            if (result.getSegments() == null) {
                continue;
            }
            for (AsrSegmentResult segment : result.getSegments()) {
                String key = String.format(
                        Locale.ROOT,
                        "%.3f|%.3f|%s",
                        segment.getStartSec() == null ? -1D : segment.getStartSec(),
                        segment.getEndSec() == null ? -1D : segment.getEndSec(),
                        segment.getText() == null ? "" : segment.getText().trim()
                );
                if (dedup.add(key)) {
                    mergedSegments.add(segment);
                }
            }
        }

        mergedSegments.sort(Comparator
                .comparing(AsrSegmentResult::getStartSec, Comparator.nullsLast(Double::compareTo))
                .thenComparing(AsrSegmentResult::getEndSec, Comparator.nullsLast(Double::compareTo)));
        for (int i = 0; i < mergedSegments.size(); i++) {
            mergedSegments.get(i).setSegmentIndex(i);
        }
        for (String part : fullTextParts) {
            if (!fullTextBuilder.isEmpty()) {
                fullTextBuilder.append(" ");
            }
            fullTextBuilder.append(part);
        }

        merged.setSegments(mergedSegments);
        merged.setFullText(fullTextBuilder.toString().trim());
        return merged;
    }

    private Double parseNumberField(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode fieldNode = node.path(field);
            if (fieldNode.isNumber()) {
                return fieldNode.asDouble();
            }
            if (fieldNode.isTextual()) {
                try {
                    return Double.parseDouble(fieldNode.asText());
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private void validateResult(AsrTranscriptionResult result) {
        if (result == null || result.getSegments() == null || result.getSegments().isEmpty()) {
            throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 结果缺少有效分段");
        }
        for (int i = 0; i < result.getSegments().size(); i++) {
            AsrSegmentResult segment = result.getSegments().get(i);
            if (segment.getText() == null || segment.getText().isBlank()) {
                throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 片段文本为空: index=" + i);
            }
            if (segment.getStartSec() == null || segment.getEndSec() == null) {
                throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 片段时间戳缺失: index=" + i);
            }
            if (segment.getEndSec() <= segment.getStartSec()) {
                throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 片段时间非法: index=" + i);
            }
            if (segment.getSegmentIndex() == null) {
                segment.setSegmentIndex(i);
            }
        }
        if (result.getFullText() == null || result.getFullText().isBlank()) {
            String joined = result.getSegments().stream()
                    .map(AsrSegmentResult::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
            result.setFullText(joined.trim());
        }
    }

    private String normalizeJsonText(String raw) {
        String text = raw.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("```json")) {
            text = text.substring(7).trim();
        } else if (lower.startsWith("```")) {
            text = text.substring(3).trim();
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3).trim();
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return text;
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
