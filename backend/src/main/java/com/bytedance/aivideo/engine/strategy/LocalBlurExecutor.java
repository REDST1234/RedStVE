package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 图片局部背景模糊策略：保留主体清晰，压虚背景。
 */
@Slf4j
@Component
public class LocalBlurExecutor implements StrategyExecutor {

    private final ObjectMapper objectMapper;

    public LocalBlurExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyType() {
        return "LOCAL_BLUR";
    }

    @Override
    public StrategyExecutionFragment buildExecutionFragment(String inputContext, boolean hasAudio) {
        StrategyExecutionFragment fragment = new StrategyExecutionFragment();
        fragment.setOutputKind(StrategyOutputKind.IMAGE_OUTPUT);
        fragment.setPreferredExtension("jpg");
        fragment.setPreserveAudio(false);
        try {
            JsonNode contextNode = objectMapper.readTree(inputContext);
            JsonNode boundingBox = contextNode.path("boundingBox");
            int boxX = boundingBox.path("x").asInt(-1);
            int boxY = boundingBox.path("y").asInt(-1);
            int boxW = boundingBox.path("w").asInt(-1);
            int boxH = boundingBox.path("h").asInt(-1);
            int sourceWidth = contextNode.path("sourceWidth").asInt(0);
            int sourceHeight = contextNode.path("sourceHeight").asInt(0);
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                throw new IllegalArgumentException("LOCAL_BLUR 缺少 sourceWidth/sourceHeight");
            }
            if (boxX < 0 || boxY < 0 || boxW <= 0 || boxH <= 0) {
                throw new IllegalArgumentException("LOCAL_BLUR boundingBox 非法");
            }

            int blurStrength = clamp(contextNode.path("blurStrength").asInt(18), 2, 40);
            int featherPercent = clamp(contextNode.path("featherPercent").asInt(8), 0, 20);
            double expandRatio = 1.0d + (featherPercent / 100.0d);

            int clearWidth = clamp((int) Math.round(boxW * expandRatio), 1, sourceWidth);
            int clearHeight = clamp((int) Math.round(boxH * expandRatio), 1, sourceHeight);
            int centerX = boxX + (boxW / 2);
            int centerY = boxY + (boxH / 2);
            int clearX = clamp(centerX - (clearWidth / 2), 0, sourceWidth - clearWidth);
            int clearY = clamp(centerY - (clearHeight / 2), 0, sourceHeight - clearHeight);

            String filterComplex = String.format(
                    "[0:v]split=2[sharp][bg];" +
                            "[bg]boxblur=luma_radius=%d:luma_power=1[blurred];" +
                            "[sharp]crop=%d:%d:%d:%d[subject];" +
                            "[blurred][subject]overlay=%d:%d[outv]",
                    blurStrength,
                    clearWidth,
                    clearHeight,
                    clearX,
                    clearY,
                    clearX,
                    clearY
            );
            fragment.setFilterComplex(filterComplex);
            fragment.getMapArgs().add("-map");
            fragment.getMapArgs().add("[outv]");
            fragment.getExtraArgs().add("-frames:v");
            fragment.getExtraArgs().add("1");
            fragment.getExtraArgs().add("-q:v");
            fragment.getExtraArgs().add("2");
        } catch (Exception e) {
            log.error("Failed to parse LocalBlur context", e);
        }
        return fragment;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
