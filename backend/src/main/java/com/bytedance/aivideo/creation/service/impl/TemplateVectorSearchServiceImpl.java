package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.creation.dto.TemplateCandidate;
import com.bytedance.aivideo.creation.service.TemplateVectorSearchService;
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
public class TemplateVectorSearchServiceImpl implements TemplateVectorSearchService {

    private final VectorStore vectorStore;

    public TemplateVectorSearchServiceImpl(
            @Qualifier("creationTemplateVectorStore") VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<TemplateCandidate> searchCandidates(String queryText, int topK) {
        log.info("开始在 creation_template 集合中检索模板候选，topK={}", topK);
        
        SearchRequest searchRequest = SearchRequest.builder().query(queryText).topK(topK).build();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        
        List<TemplateCandidate> candidates = new ArrayList<>();
        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();
            // Spring AI ChromaVectorStore returns similarity score in metadata sometimes, or we can use the distance.
            // Spring AI's similaritySearch returns documents sorted by distance (closest first).
            // Distance is usually 0.0 for exact match, higher for less similar. 
            // Chroma returns distances, we can convert it to a score [0, 1].
            // If distance is available:
            Object distanceObj = metadata.get("distance");
            double score = 0.5; // default
            if (distanceObj instanceof Number) {
                double distance = ((Number) distanceObj).doubleValue();
                // simple conversion: score = 1.0 - distance, bounded to [0, 1]
                // Note: cosine distance is 0..2, inner product is unbound, L2 is unbound. 
                // Assuming cosine distance (default in many setups):
                score = Math.max(0.0, 1.0 - (distance / 2.0));
            }

            String templateId = (String) metadata.getOrDefault("templateId", "");
            Integer templateVersion = 1;
            if (metadata.get("templateVersion") instanceof Number) {
                templateVersion = ((Number) metadata.get("templateVersion")).intValue();
            } else if (metadata.get("templateVersion") instanceof String) {
                try {
                    templateVersion = Integer.parseInt((String) metadata.get("templateVersion"));
                } catch (Exception e) {}
            }

            String categoryId = (String) metadata.getOrDefault("categoryId", "");
            
            Integer segmentCount = 0;
            if (metadata.get("segmentCount") instanceof Number) {
                segmentCount = ((Number) metadata.get("segmentCount")).intValue();
            } else if (metadata.get("segmentCount") instanceof String) {
                try {
                    segmentCount = Integer.parseInt((String) metadata.get("segmentCount"));
                } catch (Exception e) {}
            }

            if (!templateId.isBlank()) {
                candidates.add(TemplateCandidate.builder()
                        .templateId(templateId)
                        .templateVersion(templateVersion)
                        .categoryId(categoryId)
                        .segmentCount(segmentCount)
                        .semanticScore(score)
                        .build());
            }
        }
        
        log.info("向量检索完成，找到 {} 个候选模板", candidates.size());
        return candidates;
    }
}
