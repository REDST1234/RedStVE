package com.bytedance.aivideo.infrastructure.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动时自动将 MySQL category_knowledge 表同步至 Chroma 向量库。
 * 仅当 {@code vector.auto-init.enabled=true} 时生效。
 * <p>
 * 手动触发：{@code POST /api/admin/vector/init} 或运行 {@code scripts/vector-init.sh}
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vector.auto-init.enabled", havingValue = "true")
public class CategoryKnowledgeVectorInitRunner implements CommandLineRunner {

    private final VectorInitService vectorInitService;

    public CategoryKnowledgeVectorInitRunner(VectorInitService vectorInitService) {
        this.vectorInitService = vectorInitService;
    }

    @Override
    public void run(String... args) {
        log.info("vector.auto-init.enabled=true，启动时自动同步品类知识向量...");
        vectorInitService.syncCategoryKnowledge();
    }
}
