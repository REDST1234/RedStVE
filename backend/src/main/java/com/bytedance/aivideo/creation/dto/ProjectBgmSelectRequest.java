package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ProjectBgmSelectRequest {
    private String audioId;
    private String audioName;
    /**
     * 前端当前已有推荐结果字段，通常形如 /api/storage/audio-database/{folder}/{file}
     */
    private String filePath;
    private String sourceType;
    private Double recommendScore;
    private Double semanticScore;
    private Double energyCurveScore;
    private Double durationBpmScore;
    private String mixLevel;
    private Boolean loopEnabled;
    private Integer fadeInFrames;
    private Integer fadeOutFrames;
    private Boolean duckingEnabled;
    private Double duckingRatio;
    private Map<String, Object> metadata;
}
