package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ArkProperties;
import com.bytedance.aivideo.config.AsrProperties;
import com.bytedance.aivideo.creation.dto.profile.CreationAsrResult;
import com.bytedance.aivideo.creation.dto.profile.CreationAsrSegment;
import com.bytedance.aivideo.creation.service.CreationAsrService;
import com.bytedance.aivideo.infrastructure.ark.ArkPayloadFactory;
import com.bytedance.aivideo.infrastructure.ark.ArkPromptTemplates;
import com.bytedance.aivideo.infrastructure.ark.ArkResponsesClient;
import com.bytedance.aivideo.infrastructure.ark.model.ArkResponseRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class CreationAsrServiceImpl implements CreationAsrService {

    private final ArkResponsesClient arkResponsesClient;
    private final ArkPayloadFactory arkPayloadFactory;
    private final AsrProperties asrProperties;
    private final ArkProperties arkProperties;
    private final ObjectMapper objectMapper;

    public CreationAsrServiceImpl(
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
    public CreationAsrResult transcribe(Path audioFile) {
        if (audioFile == null || !Files.exists(audioFile)) {
            throw new BizException(ErrorCode.ASR_ERROR, "音频文件路径无效");
        }
        byte[] audioBytes;
        try {
            audioBytes = Files.readAllBytes(audioFile);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.ASR_ERROR, "读取音频文件失败: " + ex.getMessage());
        }
        if (audioBytes.length == 0 || audioBytes.length > asrProperties.getMaxAudioBytes()) {
            throw new BizException(ErrorCode.ASR_ERROR, "音频文件大小不合法");
        }

        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
        String prompt = ArkPromptTemplates.CREATION_PRE_ASR_PROMPT;
        int maxAttempts = Math.max(1, arkProperties.getMaxRetries() + 1);

        BizException lastBizException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String attemptPrompt = attempt == 1 ? prompt : prompt + "\n仅输出JSON，不要包含解释或代码块。";
            try {
                ArkResponseRequest request = new ArkResponseRequest();
                request.setModel(asrProperties.getDoubao().getModel());
                request.setMessages(arkPayloadFactory.buildAudioInput(base64Audio, "mp3", attemptPrompt));
                
                log.info("creation pre-asr request start: attempt={}, file={}", attempt, audioFile.getFileName());
                JsonNode responseNode = arkResponsesClient.createResponse(request);
                
                String content = extractContent(responseNode);
                CreationAsrResult result = parseResult(content);
                log.info("creation pre-asr request success: segments={}", result.getSegments() != null ? result.getSegments().size() : 0);
                return result;
            } catch (BizException ex) {
                lastBizException = ex;
                log.warn("creation pre-asr parse failed: attempt={}, reason={}", attempt, ex.getMessage());
            }
        }
        
        throw lastBizException == null
                ? new BizException(ErrorCode.ASR_RESPONSE_INVALID, "创作流 ASR 响应解析失败")
                : lastBizException;
    }

    private String extractContent(JsonNode responseNode) {
        if (responseNode == null) return null;
        JsonNode choicesNode = responseNode.path("choices");
        if (choicesNode.isArray() && !choicesNode.isEmpty()) {
            JsonNode messageNode = choicesNode.get(0).path("message");
            if (messageNode.has("content")) {
                return messageNode.get("content").asText();
            }
        }
        return null;
    }

    private CreationAsrResult parseResult(String content) {
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "ASR 返回内容为空");
        }
        
        String jsonStr = normalizeJsonText(content);
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            CreationAsrResult result = new CreationAsrResult();
            result.setFullText(root.path("fullText").asText(""));
            
            JsonNode segmentsNode = root.path("segments");
            List<CreationAsrSegment> segments = new ArrayList<>();
            if (segmentsNode.isArray()) {
                for (JsonNode segNode : segmentsNode) {
                    CreationAsrSegment segment = new CreationAsrSegment();
                    segment.setStart(segNode.path("start").asDouble(0.0));
                    segment.setEnd(segNode.path("end").asDouble(0.0));
                    segment.setText(segNode.path("text").asText(""));
                    segments.add(segment);
                }
            }
            result.setSegments(segments);
            return result;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.ASR_RESPONSE_INVALID, "解析 JSON 失败: " + ex.getMessage());
        }
    }
    
    private String normalizeJsonText(String raw) {
        String text = raw.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7).trim();
        } else if (text.startsWith("```")) {
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
}
