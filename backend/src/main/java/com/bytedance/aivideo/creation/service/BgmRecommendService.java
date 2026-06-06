package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.dto.BgmRecommendResponse;

public interface BgmRecommendService {
    /**
     * 为指定的创作项目推荐最适配的 BGM
     * @param projectId 项目ID
     * @param w1 语义匹配权重
     * @param w2 能量曲线匹配权重
     * @param w3 时长与 BPM 匹配权重
     * @param topN 返回推荐项数量限制
     * @param forceRefresh 强制刷新 — true 时跳过缓存重新计算
     */
    BgmRecommendResponse recommend(String projectId, double w1, double w2, double w3, int topN, boolean forceRefresh);
}
