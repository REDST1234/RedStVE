package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;

@Data
public class LightingProfileDto {
    private Double meanLuminance;
    private Double contrastRatio;
    private Boolean isOverExposed;
    private Boolean isUnderExposed;
    private String diagnosisStrategy; // stage_1_ffmpeg_fast, stage_1_ffmpeg_fast_fallback, stage_2_llm_diagnosed
}
