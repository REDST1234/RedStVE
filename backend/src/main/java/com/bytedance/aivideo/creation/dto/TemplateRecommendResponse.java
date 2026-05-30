package com.bytedance.aivideo.creation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TemplateRecommendResponse {
    private String projectId;
    private List<TemplateRecommendItemResponse> recommendations;
}
