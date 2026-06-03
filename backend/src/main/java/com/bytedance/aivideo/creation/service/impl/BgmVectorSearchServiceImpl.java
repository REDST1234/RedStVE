package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.creation.dto.BgmCandidate;
import com.bytedance.aivideo.creation.service.BgmVectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BgmVectorSearchServiceImpl implements BgmVectorSearchService {

    private final VectorStore vectorStore;

    public BgmVectorSearchServiceImpl(
            @Qualifier("creationBgmVectorStore") VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<BgmCandidate> searchCandidates(String queryText, int topK) {
        log.info("开始在 creation_bgm 集合中进行向量检索，queryText={}, topK={}", queryText, topK);

        SearchRequest searchRequest = SearchRequest.builder().query(queryText).topK(topK).build();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        List<BgmCandidate> candidates = new ArrayList<>();
        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();
            Object distanceObj = metadata.get("distance");
            double score = 0.5; // 默认中等
            if (distanceObj instanceof Number) {
                double distance = ((Number) distanceObj).doubleValue();
                score = Math.max(0.0, 1.0 - (distance / 2.0));
            }

            String audioId = (String) metadata.getOrDefault("audioId", "");
            String audioName = (String) metadata.getOrDefault("audioName", "");
            
            Integer bpm = 120;
            if (metadata.get("bpm") instanceof Number) {
                bpm = ((Number) metadata.get("bpm")).intValue();
            } else if (metadata.get("bpm") instanceof String) {
                try {
                    bpm = Integer.parseInt((String) metadata.get("bpm"));
                } catch (Exception e) {}
            }

            String overallStyle = (String) metadata.getOrDefault("overallStyle", "");
            
            Double durationSeconds = 0.0;
            if (metadata.get("durationSeconds") instanceof Number) {
                durationSeconds = ((Number) metadata.get("durationSeconds")).doubleValue();
            } else if (metadata.get("durationSeconds") instanceof String) {
                try {
                    durationSeconds = Double.parseDouble((String) metadata.get("durationSeconds"));
                } catch (Exception e) {}
            }

            String folderName = (String) metadata.getOrDefault("folderName", "");

            if (!audioId.isBlank()) {
                candidates.add(BgmCandidate.builder()
                        .audioId(audioId)
                        .audioName(audioName)
                        .bpm(bpm)
                        .overallStyle(overallStyle)
                        .durationSeconds(durationSeconds)
                        .folderName(folderName)
                        .semanticScore(score)
                        .build());
            }
        }

        log.info("向量检索完成，找到 {} 个候选 BGM", candidates.size());
        return candidates;
    }
}
