package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ASR 业务配置。
 */
@Component
@ConfigurationProperties(prefix = "asr")
public class AsrProperties {

    /**
     * 最大音频字节数，超过即拒绝调用外部接口。
     */
    private long maxAudioBytes = 20L * 1024 * 1024;

    /**
     * 音轨输出目录。
     */
    private String audioOutputDir = "storage/extracted-audio";

    /**
     * 方舟模型配置。
     */
    private final Doubao doubao = new Doubao();

    public long getMaxAudioBytes() {
        return maxAudioBytes;
    }

    public void setMaxAudioBytes(long maxAudioBytes) {
        this.maxAudioBytes = maxAudioBytes;
    }

    public String getAudioOutputDir() {
        return audioOutputDir;
    }

    public void setAudioOutputDir(String audioOutputDir) {
        this.audioOutputDir = audioOutputDir;
    }

    public Doubao getDoubao() {
        return doubao;
    }

    public static class Doubao {
        /**
         * 豆包模型名。
         */
        private String model = "doubao-seed-2-0-lite-260428";

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
