package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FFmpeg 音轨提取配置。
 */
@Component
@ConfigurationProperties(prefix = "media.ffmpeg")
public class FfmpegAudioExtractProperties {

    /**
     * ffmpeg 命令路径，默认依赖 PATH。
     */
    private String path = "ffmpeg";

    /**
     * 音轨提取超时时间（秒）。
     */
    private int audioTimeoutSeconds = 30;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getAudioTimeoutSeconds() {
        return audioTimeoutSeconds;
    }

    public void setAudioTimeoutSeconds(int audioTimeoutSeconds) {
        this.audioTimeoutSeconds = audioTimeoutSeconds;
    }
}

