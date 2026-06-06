package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ComfyUI 背景移除服务配置。
 * <p>
 * 对应 YAML 中 {@code comfyui} 前缀段，
 * 接入方式与 {@link SeedreamProperties} 保持一致——独立配置段、环境变量兜底默认值。
 */
@Component
@ConfigurationProperties(prefix = "comfyui")
public class ComfyuiProperties {

    /** ComfyUI 服务地址 */
    private String baseUrl = "http://localhost:8000";

    /** 抠图工作流 JSON 文件路径（相对于项目根目录或绝对路径） */
    private String workflowPath = "config/comfyui_bg_removal_workflow.json";

    /** 轮询历史记录的间隔（毫秒） */
    private int pollIntervalMs = 1000;

    /** 轮询最大等待时间（秒） */
    private int pollTimeoutSeconds = 120;

    /** HTTP 请求超时（秒） */
    private int timeoutSeconds = 60;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getWorkflowPath() {
        return workflowPath;
    }

    public void setWorkflowPath(String workflowPath) {
        this.workflowPath = workflowPath;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getPollTimeoutSeconds() {
        return pollTimeoutSeconds;
    }

    public void setPollTimeoutSeconds(int pollTimeoutSeconds) {
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
