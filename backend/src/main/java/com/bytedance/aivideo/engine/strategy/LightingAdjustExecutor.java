package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 亮度/对比度修正策略。
 */
@Slf4j
@Component
public class LightingAdjustExecutor implements StrategyExecutor {

    private static final double MIN_BRIGHTNESS = -0.30d;
    private static final double MAX_BRIGHTNESS = 0.30d;
    private static final double MIN_CONTRAST = 0.70d;
    private static final double MAX_CONTRAST = 1.60d;

    private final ObjectMapper objectMapper;

    public LightingAdjustExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyType() {
        return "LIGHTING_ADJUST";
    }

    @Override
    public StrategyExecutionFragment buildExecutionFragment(String inputContext, boolean hasAudio) {
        StrategyExecutionFragment fragment = new StrategyExecutionFragment();
        fragment.setOutputKind(StrategyOutputKind.VIDEO_OUTPUT);
        fragment.setPreferredExtension("mp4");
        fragment.setPreserveAudio(hasAudio);
        try {
            JsonNode contextNode = objectMapper.readTree(inputContext);
            double brightnessPercent = contextNode.path("brightnessPercent").asDouble(0.0d);
            double contrastPercent = contextNode.path("contrastPercent").asDouble(0.0d);
            double brightness = clamp(brightnessPercent / 100.0d, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
            double contrast = clamp(1.0d + (contrastPercent / 100.0d), MIN_CONTRAST, MAX_CONTRAST);
            if (Math.abs(brightness) < 0.0001d && Math.abs(contrast - 1.0d) < 0.0001d) {
                log.warn("LightingAdjust generated empty effect, brightnessPercent={}, contrastPercent={}", brightnessPercent, contrastPercent);
                return fragment;
            }
            fragment.getVideoFilters().add(String.format("eq=brightness=%.4f:contrast=%.4f", brightness, contrast));
        } catch (Exception e) {
            log.error("Failed to parse LightingAdjust context", e);
        }
        return fragment;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
