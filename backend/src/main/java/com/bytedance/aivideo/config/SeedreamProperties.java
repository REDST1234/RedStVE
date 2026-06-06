package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Seedream 图片生成 (doubao-seedream-4-0) 配置。
 * <p>
 * 对应 YAML 中 {@code seedream} 前缀段，
 * 接入方式与 {@link AsrProperties.Doubao} 保持一致——独立配置段、环境变量兜底默认值。
 */
@Component
@ConfigurationProperties(prefix = "seedream")
public class SeedreamProperties {

    /**
     * 模型 ID，格式为 {@code doubao-seedream-4-0-250828}。
     */
    private String model = "doubao-seedream-4-0-250828";

    /**
     * 图片生成接口路径，相对于 ark.base-url。
     */
    private String imagesPath = "/api/v3/images/generations";

    /**
     * 默认出品尺寸。
     */
    private String size = "2K";

    /**
     * 是否开启水印。
     */
    private boolean watermark = true;

    /**
     * 响应格式：{@code url} 或 {@code b64_json}。
     */
    private String responseFormat = "url";

    /**
     * 是否开启组图模式：{@code disabled} 严格与 prompt ——对应。
     */
    private String sequentialImageGeneration = "disabled";

    /**
     * 请求超时秒数（生图耗时较长，独立于 ark.timeout-seconds）。
     */
    private int timeoutSeconds = 120;

    /**
     * Seedream 独立 API Key，与 ark.api-key 隔离。
     * 若不配置则回退使用 ark.api-key。
     */
    private String apiKey;

    // ... getters/setters below

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getImagesPath() {
        return imagesPath;
    }

    public void setImagesPath(String imagesPath) {
        this.imagesPath = imagesPath;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public boolean isWatermark() {
        return watermark;
    }

    public void setWatermark(boolean watermark) {
        this.watermark = watermark;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public String getSequentialImageGeneration() {
        return sequentialImageGeneration;
    }

    public void setSequentialImageGeneration(String sequentialImageGeneration) {
        this.sequentialImageGeneration = sequentialImageGeneration;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
