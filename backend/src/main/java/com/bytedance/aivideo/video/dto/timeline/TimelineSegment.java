package com.bytedance.aivideo.video.dto.timeline;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TimelineSegment {
    private String segmentId;
    private String timeRange;
    private String phaseHint;
    private String audioVisualResonance;
    private List<AudioTextEntry> audioAndText;
    private AudioSemanticSummary audioSemanticSummary;
    private VisualDynamics visualDynamics;
    private List<KeyFrameEntry> keyFrames;
}
