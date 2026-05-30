package com.bytedance.aivideo.creation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateCandidate {
    private String templateId;
    private Integer templateVersion;
    private String categoryId;
    private Integer segmentCount;
    private Double semanticScore; // 向量检索相似度得分
}
