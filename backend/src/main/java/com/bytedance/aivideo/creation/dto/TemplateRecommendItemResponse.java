package com.bytedance.aivideo.creation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateRecommendItemResponse {
    private Integer rank;
    private String templateId;
    private Integer templateVersion;
    private String templateName;
    private String categoryId;
    private Integer segmentCount;
    private Double finalScore;
    private Double semanticScore;
    private Double structureScore;
}
