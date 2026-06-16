package com.campus.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量嵌入与检索服务接口
 */
public interface EmbeddingService {

    /**
     * 将文档块向量化并存入向量数据库
     * 同时同步存入 knowledge_doc 表
     * @param chunks 文档块列表
     */
    void embedAndStore(List<Document> chunks);

    /**
     * 相似度检索
     * @param query 查询文本
     * @param topK  返回 Top-K 文档块
     * @return      按相似度降序排列的文档块
     */
    List<Document> similaritySearch(String query, int topK);

    /**
     * 获取向量库中已存储的文档数量
     */
    int getStoreSize();
}
