package com.bytedance.aivideo.infrastructure.vector;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaApiProperties;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向量基础设施健康检查服务。
 */
@Service
public class VectorHealthService {

    private final ChromaApi chromaApi;
    private final ChromaApiProperties chromaApiProperties;
    private final ChromaVectorStoreProperties chromaVectorStoreProperties;
    private final RestClient restClient;
    private final String ollamaBaseUrl;
    private final String ollamaEmbeddingModel;

    public VectorHealthService(
            ChromaApi chromaApi,
            ChromaApiProperties chromaApiProperties,
            ChromaVectorStoreProperties chromaVectorStoreProperties,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}") String ollamaEmbeddingModel
    ) {
        this.chromaApi = chromaApi;
        this.chromaApiProperties = chromaApiProperties;
        this.chromaVectorStoreProperties = chromaVectorStoreProperties;
        this.restClient = restClientBuilderProvider.getIfAvailable(RestClient::builder).build();
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaEmbeddingModel = ollamaEmbeddingModel;
    }

    public Map<String, Object> health() {
        ComponentStatus chromaStatus = checkChroma();
        ComponentStatus ollamaStatus = checkOllama();
        boolean up = "UP".equals(chromaStatus.status()) && "UP".equals(ollamaStatus.status());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", up ? "UP" : "DOWN");
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("chroma", chromaStatus.toMap());
        data.put("ollama", ollamaStatus.toMap());
        data.put("tenantName", chromaVectorStoreProperties.getTenantName());
        data.put("databaseName", chromaVectorStoreProperties.getDatabaseName());
        data.put("collectionName", chromaVectorStoreProperties.getCollectionName());
        data.put("embeddingModel", ollamaEmbeddingModel);
        return data;
    }

    private ComponentStatus checkChroma() {
        String endpoint = String.format("%s:%s", chromaApiProperties.getHost(), chromaApiProperties.getPort());
        try {
            chromaApi.getCollection(
                    chromaVectorStoreProperties.getTenantName(),
                    chromaVectorStoreProperties.getDatabaseName(),
                    chromaVectorStoreProperties.getCollectionName()
            );
            return ComponentStatus.up(endpoint);
        } catch (Exception ex) {
            return ComponentStatus.down(endpoint, ex.getMessage());
        }
    }

    private ComponentStatus checkOllama() {
        try {
            restClient.get()
                    .uri(ollamaBaseUrl + "/api/tags")
                    .retrieve()
                    .toBodilessEntity();
            return ComponentStatus.up(ollamaBaseUrl);
        } catch (Exception ex) {
            return ComponentStatus.down(ollamaBaseUrl, ex.getMessage());
        }
    }

    private record ComponentStatus(String status, String endpoint, String errorMessage) {
        private static ComponentStatus up(String endpoint) {
            return new ComponentStatus("UP", endpoint, null);
        }

        private static ComponentStatus down(String endpoint, String errorMessage) {
            return new ComponentStatus("DOWN", endpoint, errorMessage);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", status);
            map.put("endpoint", endpoint);
            map.put("errorMessage", errorMessage);
            return map;
        }
    }
}
