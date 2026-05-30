package com.bytedance.aivideo.infrastructure.vector;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CreationTemplateVectorStoreConfig {

    @Bean("creationTemplateVectorStore")
    public VectorStore creationTemplateVectorStore(
            ChromaApi chromaApi,
            EmbeddingModel embeddingModel,
            ChromaVectorStoreProperties vectorStoreProperties,
            TemplateVectorProperties templateVectorProperties) {

        String collectionName = templateVectorProperties.getCollectionName();
        String tenantName = vectorStoreProperties.getTenantName();
        String databaseName = vectorStoreProperties.getDatabaseName();
        if (tenantName == null || tenantName.isBlank()) tenantName = "default_tenant";
        if (databaseName == null || databaseName.isBlank()) databaseName = "default_database";

        try {
            chromaApi.getCollection(tenantName, databaseName, collectionName);
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("does not exist")) {
                chromaApi.createCollection(tenantName, databaseName, new ChromaApi.CreateCollectionRequest(collectionName));
            } else {
                throw new RuntimeException("Failed to check or create Chroma collection: " + collectionName, ex);
            }
        }

        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .tenantName(tenantName)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .initializeSchema(false) // 我们已经手动建了，不需要它再建
                .build();
    }
}
