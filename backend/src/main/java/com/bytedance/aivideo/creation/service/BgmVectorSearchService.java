package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.dto.BgmCandidate;
import java.util.List;

public interface BgmVectorSearchService {
    /**
     * 根据查询描述向量检索出相似的 BGM 列表
     * @param queryText 聚合的语义查询词
     * @param topK 返回候选数量
     */
    List<BgmCandidate> searchCandidates(String queryText, int topK);
}
