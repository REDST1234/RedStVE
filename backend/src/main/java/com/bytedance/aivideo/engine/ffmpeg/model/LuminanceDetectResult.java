package com.bytedance.aivideo.engine.ffmpeg.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 亮度检测结果。
 */
@Getter
@AllArgsConstructor
public class LuminanceDetectResult {

    private final double normalizedLuminance;
    private final Double yLowAvg;
    private final Double yHighAvg;
    private final Double contrastRatio;
    private final boolean fallbackUsed;
    private final int sampleCount;
    private final String matchedPatternType;
    private final boolean contrastUnavailable;
}
