package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class ProjectBgmBindingResponse {
    private String projectId;
    private String versionId;
    private String audioId;
    private String audioName;
    private String srcPath;
    private String previewUrl;
    private String sourceType;
    private Double recommendScore;
    private Double semanticScore;
    private Double energyCurveScore;
    private Double durationBpmScore;
    private String mixLevel;
    private Double volume;
    private Boolean loopEnabled;
    private Integer fadeInFrames;
    private Integer fadeOutFrames;
    private Boolean duckingEnabled;
    private Double duckingRatio;
    private Map<String, Object> metadata;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
