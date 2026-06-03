package com.bytedance.aivideo.creation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BgmCandidate {
    private String audioId;
    private String audioName;
    private Integer bpm;
    private String overallStyle;
    private Double durationSeconds;
    private String folderName;
    private Double semanticScore;
}
