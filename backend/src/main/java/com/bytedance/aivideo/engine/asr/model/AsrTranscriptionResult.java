package com.bytedance.aivideo.engine.asr.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * ASR 转写结果聚合。
 */
@Data
public class AsrTranscriptionResult {

    private String fullText;

    private List<AsrSegmentResult> segments = new ArrayList<>();
}

