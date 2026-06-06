package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * SFX 音效自动映射配置。
 * <p>
 * 当编排 LLM 输出的 {@code media.audio} 层缺少 {@code src} 但有 {@code cueType} 时，
 * 后端根据此映射自动填入对应的默认音频 URL。
 * <p>
 * 对应 YAML 中 {@code sfx-audio} 前缀段。
 */
@Component
@ConfigurationProperties(prefix = "sfx-audio")
public class SfxAudioProperties {

    /**
     * cueType → 音频文件 URL 的映射。
     * <p>
     * key: cueType 枚举值 (如 {@code typewriter_loop})
     * value: 音频文件的可访问 URL (如 {@code http://localhost:3001/storage/sfx/typewriter_loop.mp3})
     */
    private Map<String, String> mappings = new HashMap<>();

    public Map<String, String> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> mappings) {
        this.mappings = mappings;
    }

    /**
     * 根据 cueType 查找默认音频 URL，未配置时返回 {@code null}。
     */
    public String resolveSrc(String cueType) {
        if (cueType == null || cueType.isBlank() || mappings == null) {
            return null;
        }
        return mappings.get(cueType.trim());
    }
}
