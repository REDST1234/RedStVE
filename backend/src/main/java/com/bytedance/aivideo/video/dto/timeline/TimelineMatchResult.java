package com.bytedance.aivideo.video.dto.timeline;

import lombok.Data;
import java.util.List;

@Data
public class TimelineMatchResult {
    private SystemMeta systemMeta;
    private List<TimelineSegment> timelineSegments;

    @Data
    public static class SystemMeta {
        private double totalDuration;
        private double appliedSceneThreshold;
        private int totalKeyFramesExtracted;
        private List<Double> visualWaveform;
    }
}
