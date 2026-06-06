package com.bytedance.aivideo.creation.dto;

import lombok.Data;

@Data
public class BgmRecommendRequest {
    /**
     * 语义匹配权重 (w1)，默认 0.35
     */
    private Double w1 = 0.35;

    /**
     * 能量曲线拟合权重 (w2)，默认 0.50
     */
    private Double w2 = 0.50;

    /**
     * 时长与 BPM 物理适配权重 (w3)，默认 0.15
     */
    private Double w3 = 0.15;

    /**
     * 返回推荐结果的限制数量，默认 5
     */
    private Integer topN = 5;

    /** 强制刷新 — 设为 true 时跳过缓存重新计算 */
    private Boolean forceRefresh = false;
}
