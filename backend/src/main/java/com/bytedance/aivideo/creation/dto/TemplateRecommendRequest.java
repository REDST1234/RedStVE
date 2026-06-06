package com.bytedance.aivideo.creation.dto;

import lombok.Data;

@Data
public class TemplateRecommendRequest {
    private Double w1 = 0.6; // 语义权重
    private Double w2 = 0.4; // 结构权重
    private Integer topN = 10;
    /** 强制刷新 — 设为 true 时跳过缓存重新计算 */
    private Boolean forceRefresh = false;
}
