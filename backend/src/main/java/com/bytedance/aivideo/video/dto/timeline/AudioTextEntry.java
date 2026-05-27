package com.bytedance.aivideo.video.dto.timeline;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AudioTextEntry {
    private String text;
    private String timestamp;
    private String audioEmotion;
    private String volumeIntensity;
    private String backgroundEnvironment;
    private String vocalVibe;
    private String bgmGenre;
    private String bgmInstruments;
}
