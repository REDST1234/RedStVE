package com.bytedance.aivideo.infrastructure.ark;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ArkProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 方舟通用 HTTP 客户端。
 */
@Component
@Slf4j
public class ArkClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ArkProperties arkProperties;

    public ArkClient(ObjectMapper objectMapper, ArkProperties arkProperties) {
        this.objectMapper = objectMapper;
        this.arkProperties = arkProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, arkProperties.getTimeoutSeconds())))
                .build();
    }

    public JsonNode postJson(String path, Object payload) {
        if (arkProperties.getApiKey() == null || arkProperties.getApiKey().isBlank()) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "ARK_API_KEY 未配置");
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "方舟请求序列化失败: " + ex.getMessage());
        }

        String url = normalizeUrl(path);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, arkProperties.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + arkProperties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            long startMs = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("ark http finished: path={}, statusCode={}, elapsedMs={}", path, response.statusCode(), elapsedMs);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(
                        ErrorCode.ARK_API_ERROR,
                        "方舟接口调用失败，status=" + response.statusCode() + ", body=" + response.body()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.ARK_API_ERROR, "方舟调用被中断: " + ex.getMessage());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "方舟调用失败: " + ex.getMessage());
        }
    }

    private String normalizeUrl(String path) {
        String base = arkProperties.getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "ark.base-url 未配置");
        }
        if (path == null || path.isBlank()) {
            return base;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }
}

