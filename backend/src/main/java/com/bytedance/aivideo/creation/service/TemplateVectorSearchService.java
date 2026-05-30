package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.dto.TemplateCandidate;

import java.util.List;

public interface TemplateVectorSearchService {
    
    /**
     * 基于素材画像文本在向量库中检索 TOP-K 模板候选
     * @param queryText 素材画像聚合而成的自然语言文本
     * @param topK 需要返回的候选数量
     * @return 模板候选列表，按相似度降序排序
     */
    List<TemplateCandidate> searchCandidates(String queryText, int topK);
}
