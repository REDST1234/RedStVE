package com.bytedance.aivideo.creation.dto;

import lombok.Data;

@Data
public class CreativeAssetGridItemResponse {
    private Integer pageIndex;
    private String status;
    private String filePath;
    private String cacheKey;
    private Boolean llmIncluded;
}
