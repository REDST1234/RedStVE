package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 智能居中裁剪策略
 * 根据空间锚点(bounding_box)裁剪视频，遇到幻觉坐标自动降级为居中裁剪 (Center Crop)
 */
@Slf4j
@Component
public class SmartCropExecutor implements StrategyExecutor {

    private final ObjectMapper objectMapper;

    public SmartCropExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyType() {
        return "SMART_CROP";
    }

    @Override
    public StrategyExecutionFragment buildExecutionFragment(String inputContext, boolean hasAudio) {
        StrategyExecutionFragment fragment = new StrategyExecutionFragment();
        fragment.setOutputKind(StrategyOutputKind.VIDEO_OUTPUT);
        fragment.setPreferredExtension("mp4");
        fragment.setPreserveAudio(hasAudio);
        try {
            JsonNode contextNode = objectMapper.readTree(inputContext);
            int targetW = contextNode.path("targetWidth").asInt(1080);
            int targetH = contextNode.path("targetHeight").asInt(1920);

            if (targetW <= 0 || targetH <= 0) {
                log.warn("Invalid smart crop target size, fallback to default portrait: {}x{}", targetW, targetH);
                targetW = 1080;
                targetH = 1920;
            }

            // BB 坐标来自源素材像素空间。这里先求安全裁剪窗，再缩放到目标分辨率，避免直接 crop 越界。
            JsonNode bb = contextNode.path("boundingBox");
            int boxX = bb.path("x").asInt(-1);
            int boxY = bb.path("y").asInt(-1);
            int boxW = bb.path("w").asInt(-1);
            int boxH = bb.path("h").asInt(-1);

            String cropFilter = buildSafeCropFilter(targetW, targetH, boxX, boxY, boxW, boxH);
            fragment.getVideoFilters().add(cropFilter);

        } catch (Exception e) {
            log.error("Failed to parse SmartCrop context", e);
        }
        return fragment;
    }

    private String buildSafeCropFilter(int targetW, int targetH, int boxX, int boxY, int boxW, int boxH) {
        double targetAspect = (double) targetW / (double) targetH;
        String targetAspectLiteral = formatDouble(targetAspect);

        String cropWidthExpr = String.format(
                Locale.ROOT,
                "if(gte(iw/ih\\,%s)\\,floor(ih*%s)\\,iw)",
                targetAspectLiteral,
                targetAspectLiteral
        );
        String cropHeightExpr = String.format(
                Locale.ROOT,
                "if(gte(iw/ih\\,%s)\\,ih\\,floor(iw/%s))",
                targetAspectLiteral,
                targetAspectLiteral
        );

        boolean hasValidBoundingBox = boxX >= 0 && boxY >= 0 && boxW > 0 && boxH > 0;
        String focusCenterXExpr;
        String focusCenterYExpr;
        if (hasValidBoundingBox) {
            focusCenterXExpr = formatDouble(boxX + (boxW / 2.0d));
            focusCenterYExpr = formatDouble(boxY + (boxH / 2.0d));
        } else {
            log.info("Smart crop bounding box unavailable or invalid, fallback to center crop: x={}, y={}, w={}, h={}",
                    boxX, boxY, boxW, boxH);
            focusCenterXExpr = "iw/2";
            focusCenterYExpr = "ih/2";
        }

        String cropXExpr;
        String cropYExpr;
        if (hasValidBoundingBox) {
            cropXExpr = String.format(
                    Locale.ROOT,
                    "max(0\\,min(iw-%s\\,%s-(%s/2)))",
                    cropWidthExpr,
                    focusCenterXExpr,
                    cropWidthExpr
            );
            cropYExpr = String.format(
                    Locale.ROOT,
                    "max(0\\,min(ih-%s\\,%s-(%s/2)))",
                    cropHeightExpr,
                    focusCenterYExpr,
                    cropHeightExpr
            );
        } else {
            cropXExpr = String.format(Locale.ROOT, "(iw-%s)/2", cropWidthExpr);
            cropYExpr = String.format(Locale.ROOT, "(ih-%s)/2", cropHeightExpr);
        }

        return String.format(
                Locale.ROOT,
                "crop=%s:%s:%s:%s,scale=%d:%d",
                cropWidthExpr,
                cropHeightExpr,
                cropXExpr,
                cropYExpr,
                targetW,
                targetH
        );
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
