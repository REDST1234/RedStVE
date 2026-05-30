package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ArkProperties;
import com.bytedance.aivideo.creation.dto.profile.PhysicalAttributesDto;
import com.bytedance.aivideo.creation.service.MultimodalLlmService;
import com.bytedance.aivideo.infrastructure.ark.ArkPayloadFactory;
import com.bytedance.aivideo.infrastructure.ark.ArkPromptTemplates;
import com.bytedance.aivideo.infrastructure.ark.ArkResponsesClient;
import com.bytedance.aivideo.infrastructure.ark.model.ArkInputMessage;
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
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class MultimodalLlmServiceImpl implements MultimodalLlmService {

    private final ArkPayloadFactory arkPayloadFactory;
    private final ArkResponsesClient arkResponsesClient;
    private final ArkProperties arkProperties;
    private final ObjectMapper objectMapper;

    public MultimodalLlmServiceImpl(
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
    public String analyzeProfile(
            String materialType,
            List<Path> imagePaths,
            Optional<String> asrText,
            String textContent,
            PhysicalAttributesDto physicalAttributes
    ) {
        String normalizedType = materialType == null ? "" : materialType.trim().toUpperCase(Locale.ROOT);
        String physicalJson = serializePhysicalAttributes(physicalAttributes);
        String prompt = buildPrompt(normalizedType, physicalJson, asrText, textContent);
        List<ArkInputMessage> messages = buildMessages(normalizedType, imagePaths, prompt);

        ArkResponseRequest request = new ArkResponseRequest();
        request.setModel(arkProperties.getModel());
        request.setMessages(messages);

        JsonNode response = arkResponsesClient.createResponse(request);
        String content = extractContent(response);
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "素材分析 LLM 返回空内容");
        }
        return stripMarkdownJsonFence(content);
    }

    private String buildPrompt(String materialType, String physicalJson, Optional<String> asrText, String textContent) {
        return switch (materialType) {
            case "VIDEO" -> String.format(
                    ArkPromptTemplates.CREATION_PROFILE_VIDEO_JSON,
                    physicalJson,
                    asrText.orElse("")
            );
            case "IMAGE" -> String.format(
                    ArkPromptTemplates.CREATION_PROFILE_IMAGE_JSON,
                    physicalJson
            );
            case "TEXT" -> String.format(
                    ArkPromptTemplates.CREATION_PROFILE_TEXT_JSON,
                    physicalJson,
                    textContent == null ? "" : textContent
            );
            default -> throw new BizException(ErrorCode.INVALID_REQUEST, "不支持的素材类型: " + materialType);
        };
    }

    private List<ArkInputMessage> buildMessages(String materialType, List<Path> imagePaths, String prompt) {
        if ("TEXT".equals(materialType)) {
            return arkPayloadFactory.buildTextInput(prompt);
        }
        List<String> base64Images = new ArrayList<>();
        if (imagePaths != null) {
            for (Path imagePath : imagePaths) {
                if (imagePath == null) {
                    continue;
                }
                try {
                    if (Files.exists(imagePath)) {
                        base64Images.add(Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath)));
                    }
                } catch (IOException ex) {
                    log.warn("read image bytes failed for multimodal profile: path={}, reason={}", imagePath, ex.getMessage());
                }
            }
        }
        if (base64Images.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, materialType + " 素材分析缺少图片输入");
        }
        return arkPayloadFactory.buildMultimodalInput(base64Images, prompt);
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
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

    private String serializePhysicalAttributes(PhysicalAttributesDto physicalAttributes) {
        try {
            return objectMapper.writeValueAsString(physicalAttributes == null ? new Object() : physicalAttributes);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
