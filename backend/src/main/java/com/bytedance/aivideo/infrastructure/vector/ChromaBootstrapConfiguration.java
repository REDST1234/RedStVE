package com.bytedance.aivideo.infrastructure.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaApiProperties;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Chroma 启动引导：
 * 1) 显式构造 ChromaApi
 * 2) 在应用启动时检查（或初始化）tenant/database/collection
 */
@Configuration
@Slf4j
public class ChromaBootstrapConfiguration {

    @Bean
    public ChromaApi chromaApi(
            ChromaApiProperties apiProperties,
            ChromaVectorStoreProperties vectorStoreProperties,
            TemplateVectorProperties templateVectorProperties,
            BgmVectorProperties bgmVectorProperties,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectMapper objectMapper
    ) {
        String baseUrl = String.format("%s:%s", apiProperties.getHost(), apiProperties.getPort());
        ChromaApi chromaApi = ChromaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .objectMapper(objectMapper)
                .build();

        if (hasText(apiProperties.getKeyToken())) {
            chromaApi.withKeyToken(apiProperties.getKeyToken());
        } else if (hasText(apiProperties.getUsername()) && hasText(apiProperties.getPassword())) {
            chromaApi.withBasicAuthCredentials(apiProperties.getUsername(), apiProperties.getPassword());
        }

        boolean initializeSchema = vectorStoreProperties.isInitializeSchema();
        ensureSchema(
                chromaApi,
                vectorStoreProperties.getTenantName(),
                vectorStoreProperties.getDatabaseName(),
                vectorStoreProperties.getCollectionName(),
                initializeSchema
        );

        // 额外 ensure creation_template collection
        ensureSchema(
                chromaApi,
                vectorStoreProperties.getTenantName(),
                vectorStoreProperties.getDatabaseName(),
                templateVectorProperties.getCollectionName(),
                true // 强制创建，避免报 Collection does not exist
        );

        // 额外 ensure creation_bgm collection
        ensureSchema(
                chromaApi,
                vectorStoreProperties.getTenantName(),
                vectorStoreProperties.getDatabaseName(),
                bgmVectorProperties.getCollectionName(),
                true // 强制创建，避免报 Collection does not exist
        );

        return chromaApi;
    }

    private void ensureSchema(
            ChromaApi chromaApi,
            String tenantName,
            String databaseName,
            String collectionName,
            boolean initializeSchema
    ) {
        if (!hasText(tenantName) || !hasText(databaseName) || !hasText(collectionName)) {
            throw new IllegalStateException("Chroma tenant/database/collection 配置不能为空");
        }

        ensureTenant(chromaApi, tenantName, initializeSchema);
        ensureDatabase(chromaApi, tenantName, databaseName, initializeSchema);
        ensureCollection(chromaApi, tenantName, databaseName, collectionName, initializeSchema);
    }

    private void ensureTenant(ChromaApi chromaApi, String tenantName, boolean initializeSchema) {
        try {
            chromaApi.getTenant(tenantName);
            log.info("chroma tenant ready: {}", tenantName);
        } catch (RuntimeException ex) {
            if (isNotFound(ex) && initializeSchema) {
                chromaApi.createTenant(tenantName);
                log.info("chroma tenant created: {}", tenantName);
                return;
            }
            log.warn("chroma tenant unavailable: {}", tenantName);
        }
    }

    private void ensureDatabase(ChromaApi chromaApi, String tenantName, String databaseName, boolean initializeSchema) {
        try {
            chromaApi.getDatabase(tenantName, databaseName);
            log.info("chroma database ready: {}/{}", tenantName, databaseName);
        } catch (RuntimeException ex) {
            if (isNotFound(ex) && initializeSchema) {
                chromaApi.createDatabase(tenantName, databaseName);
                log.info("chroma database created: {}/{}", tenantName, databaseName);
                return;
            }
            log.warn("chroma database unavailable: {}/{}", tenantName, databaseName);
        }
    }

    private void ensureCollection(
            ChromaApi chromaApi,
            String tenantName,
            String databaseName,
            String collectionName,
            boolean initializeSchema
    ) {
        try {
            chromaApi.getCollection(tenantName, databaseName, collectionName);
            log.info("chroma collection ready: {}/{}/{}", tenantName, databaseName, collectionName);
        } catch (RuntimeException ex) {
            if (isNotFound(ex) && initializeSchema) {
                chromaApi.createCollection(tenantName, databaseName, new ChromaApi.CreateCollectionRequest(collectionName));
                log.info("chroma collection created: {}/{}/{}", tenantName, databaseName, collectionName);
                return;
            }
            log.warn("chroma collection unavailable: {}/{}/{}", tenantName, databaseName, collectionName);
        }
    }

    private boolean isNotFound(RuntimeException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("does not exist");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
