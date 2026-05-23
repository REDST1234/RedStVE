package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FFmpeg 引擎配置（当前只含 ffprobe）。
 */
@Component
@ConfigurationProperties(prefix = "media.ffprobe")
public class FfmpegEngineProperties {

    /**
     * ffprobe 命令路径，默认依赖 PATH。
     */
    private String path = "ffprobe";

    /**
     * ffprobe 超时时间（秒）。
     */
    private int timeoutSeconds = 20;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
