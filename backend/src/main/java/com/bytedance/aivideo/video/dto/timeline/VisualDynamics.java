package com.bytedance.aivideo.video.dto.timeline;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VisualDynamics {
    private int rawCutsCount;
    private double cuttingVelocity;
    private double avgSceneScore;
    private String translatedAction;
}
