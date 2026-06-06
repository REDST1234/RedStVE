package com.bytedance.aivideo.infrastructure.ark.aspect;

import com.bytedance.aivideo.infrastructure.ark.model.ArkInputContent;
import com.bytedance.aivideo.infrastructure.ark.model.ArkInputMessage;
import com.bytedance.aivideo.infrastructure.ark.model.ArkResponseRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 引擎日志切面：
 * 1) 发送前记录提示词
 * 2) 响应后记录返回结果
 */
@Aspect
@Component
@Slf4j
public class AiEngineLogAspect {

    private static final int MAX_LOG_LENGTH = Integer.MAX_VALUE;

    private final ObjectMapper objectMapper;

    public AiEngineLogAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("execution(* com.bytedance.aivideo.infrastructure.ark.ArkResponsesClient.createResponse(..)) && args(request)")
    public Object logAiPromptAndResponse(ProceedingJoinPoint joinPoint, ArkResponseRequest request) throws Throwable {
        String traceId = UUID.randomUUID().toString();
        String model = request == null ? null : request.getModel();
        String prompt = extractPrompt(request);
        int audioUriLength = extractAudioDataLength(request);

        log.info("ai request start: traceId={}, model={}, prompt={}", traceId, model, limit(prompt));
        if (audioUriLength > 0) {
            log.info("ai request media: traceId={}, audioDataUriLength={}", traceId, audioUriLength);
        }

        long startMs = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("ai response finished: traceId={}, elapsedMs={}, response={}",
                    traceId, elapsedMs, limit(formatResponseForLog(result)));
            return result;
        } catch (Throwable ex) {
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.error("ai request failed: traceId={}, elapsedMs={}, reason={}",
                    traceId, elapsedMs, ex.getMessage(), ex);
            throw ex;
        }
    }

    private String extractPrompt(ArkResponseRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        List<String> prompts = new ArrayList<>();
        for (ArkInputMessage message : request.getMessages()) {
            if (message == null || message.getContent() == null || message.getContent().isEmpty()) {
                continue;
            }
            for (ArkInputContent content : message.getContent()) {
                if (content == null || content.getText() == null || content.getText().isBlank()) {
                    continue;
                }
                prompts.add(content.getText().trim());
            }
        }
        return String.join(" | ", prompts);
    }

    private int extractAudioDataLength(ArkResponseRequest request) {
        if (request == null || request.getMessages() == null) {
            return 0;
        }
        for (ArkInputMessage message : request.getMessages()) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            for (ArkInputContent content : message.getContent()) {
                if (content == null) {
                    continue;
                }
                if (content.getInputAudio() != null
                        && content.getInputAudio().getData() != null
                        && !content.getInputAudio().getData().isBlank()) {
                    return content.getInputAudio().getData().length();
                }
                if (content.getAudioUrl() != null && !content.getAudioUrl().isBlank()) {
                    return content.getAudioUrl().length();
                }
            }
        }
        return 0;
    }

    private String toJsonSafely(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            return String.valueOf(obj);
        }
    }

    private String formatResponseForLog(Object result) {
        if (!(result instanceof JsonNode root)) {
            return toJsonSafely(result);
        }
        try {
            JsonNode choices = root.path("choices");
            if (choices.isArray()) {
                for (JsonNode choice : choices) {
                    JsonNode message = choice.path("message");
                    if (message.isObject()) {
                        String content = textValue(message.get("content"));
                        if (content != null) {
                            JsonNode contentJson = parseStructuredContent(content);
                            if (contentJson != null) {
                                return objectMapper.writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(contentJson);
                            }
                        }
                    }
                }
            }
            return "\"content_json_parse_failed\"";
        } catch (Exception ex) {
            log.warn("Failed to extract content_json for ai logging", ex);
            return "\"content_json_parse_failed\"";
        }
    }

    private JsonNode parseStructuredContent(String content) {
        String normalized = normalizeText(content);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String stripped = stripMarkdownJsonFence(normalized);
        try {
            return objectMapper.readTree(stripped);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stripMarkdownJsonFence(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.startsWith("```")) {
            int firstLineEnd = cleaned.indexOf('\n');
            if (firstLineEnd >= 0) {
                cleaned = cleaned.substring(firstLineEnd + 1).trim();
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replace("\uFEFF", "")
                .replace("\u00A0", " ")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_LOG_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_LENGTH) + "...(truncated)";
    }
}
