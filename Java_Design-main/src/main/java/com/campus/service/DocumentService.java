package com.campus.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档加载与分块服务接口
 */
public interface DocumentService {

    /**
     * 从 resources/knowledge/ 目录加载所有 .txt 知识文档
     * @return 原始文档列表（每篇文件一个 Document）
     */
    List<Document> loadDocuments();

    /**
     * 将文档按语义块切分
     * @param documents    原始文档列表
     * @param chunkSize    每块目标大小（token 数）
     * @param overlap      块间重叠大小（token 数）
     * @return             切分后的文档块列表
     */
    List<Document> splitDocuments(List<Document> documents, int chunkSize, int overlap);
}
