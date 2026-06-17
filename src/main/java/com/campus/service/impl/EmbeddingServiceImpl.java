package com.campus.service.impl;

import com.campus.entity.KnowledgeDoc;
import com.campus.repository.KnowledgeDocMapper;
import com.campus.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 向量嵌入与检索服务实现
 * 使用 DashScope EmbeddingModel (text-embedding-v3) + SimpleVectorStore
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final EmbeddingModel embeddingModel;
    private final SimpleVectorStore vectorStore;
    private final KnowledgeDocMapper knowledgeDocMapper;

    public EmbeddingServiceImpl(EmbeddingModel embeddingModel,
                                SimpleVectorStore vectorStore,
                                KnowledgeDocMapper knowledgeDocMapper) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.knowledgeDocMapper = knowledgeDocMapper;
    }

    @Override
    public void embedAndStore(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("文档块列表为空，跳过向量化存储");
            return;
        }

        log.info("开始向量化存储: {} 个文档块, embeddingModel={}",
                chunks.size(), embeddingModel.getClass().getSimpleName());

        // 1. 向量化并存入 SimpleVectorStore
        vectorStore.add(chunks);

        // 2. 同步写入 knowledge_doc 表
        int saved = 0;
        for (Document chunk : chunks) {
            try {
                KnowledgeDoc doc = new KnowledgeDoc();
                doc.setTitle(String.valueOf(chunk.getMetadata().getOrDefault("title", "未知")));
                doc.setCategory(String.valueOf(chunk.getMetadata().getOrDefault("category", "其他")));
                doc.setContent(chunk.getText());
                doc.setVectorId(chunk.getId());
                doc.setCreateTime(LocalDateTime.now());
                knowledgeDocMapper.insert(doc);
                saved++;
            } catch (Exception e) {
                log.error("保存知识文档到数据库失败: {}", chunk.getMetadata().get("title"), e);
            }
        }

        log.info("向量化存储完成: 向量库 {} 块, 数据库 {} 条", chunks.size(), saved);
    }

    @Override
    public List<Document> similaritySearch(String query, int topK) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build()
        );
        log.debug("向量相似度检索: query=\"{}\", topK={}, 结果数={}",
                query.substring(0, Math.min(50, query.length())), topK, results.size());
        return results;
    }

    @Override
    public int getStoreSize() {
        try {
            List<Document> all = vectorStore.similaritySearch(
                    SearchRequest.builder().query("").topK(100000).build()
            );
            return all.size();
        } catch (Exception e) {
            log.warn("获取向量库大小失败", e);
            return 0;
        }
    }
}
