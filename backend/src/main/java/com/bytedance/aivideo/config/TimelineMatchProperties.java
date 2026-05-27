package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TimelineMatcher 参数配置。
 */
@Component
@ConfigurationProperties(prefix = "timeline")
public class TimelineMatchProperties {

    /**
     * 最小时长阈值（秒）：低于该值且不含关键帧的 segment 会被后置合并。
     */
    private double minSegmentDurationSec = 0.60D;

    /**
     * 浮点比较容差，用于边界时间归属判断。
     */
    private double floatEpsilon = 1e-6D;

    public double getMinSegmentDurationSec() {
        return minSegmentDurationSec;
    }

    public void setMinSegmentDurationSec(double minSegmentDurationSec) {
        this.minSegmentDurationSec = minSegmentDurationSec;
    }

    public double getFloatEpsilon() {
        return floatEpsilon;
    }

    public void setFloatEpsilon(double floatEpsilon) {
        this.floatEpsilon = floatEpsilon;
    }
}
