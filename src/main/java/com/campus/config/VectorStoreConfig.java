package com.campus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置
 * 配置 SimpleVectorStore 和 EmbeddingModel Bean
 */
@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    /**
     * 内存向量存储（SimpleVectorStore）
     * 由 EmbeddingModel 自动注入（DashScope embedding 实现）
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        log.info("初始化 SimpleVectorStore, embeddingModel={}", embeddingModel.getClass().getSimpleName());
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
