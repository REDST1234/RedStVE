package com.bytedance.aivideo.infrastructure.ark;

import com.bytedance.aivideo.config.ArkProperties;
import com.bytedance.aivideo.infrastructure.ark.model.ArkResponseRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * 方舟 chat/completions 协议客户端。
 */
@Component
public class ArkResponsesClient {

    private final ArkClient arkClient;
    private final ArkProperties arkProperties;

    public ArkResponsesClient(ArkClient arkClient, ArkProperties arkProperties) {
        this.arkClient = arkClient;
        this.arkProperties = arkProperties;
    }

    public JsonNode createResponse(ArkResponseRequest request) {
        return arkClient.postJson(arkProperties.getChatCompletionsPath(), request);
    }
}
