package com.bytedance.aivideo.engine.asr.model;

import lombok.Data;

/**
 * ASR 句级片段结果。
 */
@Data
public class AsrSegmentResult {

    private Integer segmentIndex;

    private Double startSec;

    private Double endSec;

    private String text;

    private Double confidence;

    private String speakerLabel;
}

