package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FFmpeg 通用命令配置。
 */
@Component
@ConfigurationProperties(prefix = "media.ffmpeg")
public class FfmpegCommandProperties {

    /**
     * ffmpeg 命令路径，默认依赖 PATH。
     */
    private String path = "ffmpeg";

    /**
     * 通用操作超时时间（秒），用于非音轨提取场景的默认超时。
     */
    private int operationTimeoutSeconds = 120;

    /**
     * 宫格分页最大页数，防止超长视频触发过高成本。
     */
    private int gridMaxPages = 12;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getOperationTimeoutSeconds() {
        return operationTimeoutSeconds;
    }

    public void setOperationTimeoutSeconds(int operationTimeoutSeconds) {
        this.operationTimeoutSeconds = operationTimeoutSeconds;
    }

    public int getGridMaxPages() {
        return gridMaxPages;
    }

    public void setGridMaxPages(int gridMaxPages) {
        this.gridMaxPages = gridMaxPages;
    }
}
