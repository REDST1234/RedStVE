package com.bytedance.aivideo.infrastructure.ark.seedream;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.SeedreamProperties;
import com.bytedance.aivideo.infrastructure.ark.ArkClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Seedream 图片生成 HTTP 客户端，复用 {@link ArkClient} 的鉴权与网络层。
 * <p>
 * 目标路径为 {@link SeedreamProperties#getImagesPath()} (默认为 {@code /api/v3/images/generations})。
 */
@Component
@Slf4j
public class SeedreamImageClient {

    private final ArkClient arkClient;
    private final SeedreamProperties seedreamProperties;
    private final ObjectMapper objectMapper;

    public SeedreamImageClient(ArkClient arkClient,
                               SeedreamProperties seedreamProperties,
                               ObjectMapper objectMapper) {
        this.arkClient = arkClient;
        this.seedreamProperties = seedreamProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交图片生成请求并返回结构化响应。
     *
     * @param request 请求体
     * @return 响应 JSON 反序列化为 {@link SeedreamResponse}
     */
    public SeedreamResponse generate(SeedreamRequest request) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            request.setModel(seedreamProperties.getModel());
        }
        if (request.getSize() == null) {
            request.setSize(seedreamProperties.getSize());
        }
        if (request.getWatermark() == null) {
            request.setWatermark(false);
        }
        if (request.getResponseFormat() == null) {
            request.setResponseFormat(seedreamProperties.getResponseFormat());
        }
        if (request.getSequentialImageGeneration() == null) {
            request.setSequentialImageGeneration(seedreamProperties.getSequentialImageGeneration());
        }
        if (request.getStream() == null) {
            request.setStream(false);
        }

        JsonNode raw = arkClient.postJson(seedreamProperties.getImagesPath(), request, seedreamProperties.getApiKey());

        try {
            SeedreamResponse response = objectMapper.treeToValue(raw, SeedreamResponse.class);
            if (response.getData() == null || response.getData().isEmpty()) {
                throw new BizException(ErrorCode.ARK_API_ERROR,
                        "Seedream 生图返回空结果: model=" + request.getModel());
            }
            log.info("seedream image generated: model={}, imageCount={}", request.getModel(), response.getData().size());
            return response;
        } catch (IOException ex) {
            // fallback: 尝试从 raw 中提取 url
            if (raw.has("data") && raw.get("data").isArray() && !raw.get("data").isEmpty()) {
                try {
                    return objectMapper.treeToValue(raw, SeedreamResponse.class);
                } catch (IOException ignored) {
                    // ignore fallback failure
                }
            }
            throw new BizException(ErrorCode.ARK_API_ERROR,
                    "Seedream 响应反序列化失败: " + ex.getMessage());
        }
    }

    /**
     * 快捷方法——仅需 prompt。
     */
    public SeedreamResponse generate(String prompt) {
        return generate(SeedreamRequest.of(seedreamProperties.getModel(), prompt));
    }
}
