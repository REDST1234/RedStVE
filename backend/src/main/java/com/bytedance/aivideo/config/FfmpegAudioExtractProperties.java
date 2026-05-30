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
     * 音轨提取超时时间（秒）。
     */
    private int audioTimeoutSeconds = 120;

    public int getAudioTimeoutSeconds() {
        return audioTimeoutSeconds;
    }

    public void setAudioTimeoutSeconds(int audioTimeoutSeconds) {
        this.audioTimeoutSeconds = audioTimeoutSeconds;
    }
}
