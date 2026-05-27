package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山方舟通用客户端配置。
 */
@Component
@ConfigurationProperties(prefix = "ark")
public class ArkProperties {

    /**
     * 方舟网关地址。
     */
    private String baseUrl = "https://ark.cn-beijing.volces.com";

    /**
     * Bearer 鉴权密钥。
     */
    private String apiKey;

    /**
     * 方舟推理端点或模型 ID (Endpoint ID)。
     */
    private String model;

    /**
     * Chat Completions 协议路径。
     */
    private String chatCompletionsPath = "/api/v3/chat/completions";

    /**
     * 请求超时时间（秒）。
     */
    private int timeoutSeconds = 30;

    /**
     * 解析失败重试次数。
     */
    private int maxRetries = 1;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getChatCompletionsPath() {
        return chatCompletionsPath;
    }

    public void setChatCompletionsPath(String chatCompletionsPath) {
        this.chatCompletionsPath = chatCompletionsPath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
