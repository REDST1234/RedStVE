package com.bytedance.aivideo.video.dto.timeline;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AudioSemanticSummary {
    private String vocalVibeSummary;
    private String bgmGenreSummary;
    private String bgmInstrumentsSummary;
}
