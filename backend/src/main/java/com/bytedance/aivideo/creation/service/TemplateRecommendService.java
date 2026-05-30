package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.dto.TemplateRecommendResponse;

public interface TemplateRecommendService {
    /**
     * 基于项目素材推荐 TOP-N 模板
     * @param projectId 项目ID
     * @param w1 语义权重
     * @param w2 结构权重
     * @param topN 返回数量
     * @return 推荐结果
     */
    TemplateRecommendResponse recommend(String projectId, double w1, double w2, int topN);
}
