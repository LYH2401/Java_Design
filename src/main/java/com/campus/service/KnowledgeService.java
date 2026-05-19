package com.campus.service;

import com.campus.entity.KnowledgeDoc;

import java.util.List;

/**
 * 校园知识库服务接口
 * 提供关键词检索、RAG 上下文构建、知识库管理能力
 */
public interface KnowledgeService {

    /**
     * 关键词检索知识库文档
     * @param query     用户查询文本
     * @param topK      最大返回条数
     * @return          按相关度排序的知识文档列表
     */
    List<KnowledgeDoc> search(String query, int topK);

    /**
     * 基于用户问题构建 RAG 增强 Prompt
     * @param userMessage  用户原始问题
     * @return             注入知识上下文后的增强 Prompt
     */
    String buildRagPrompt(String userMessage);

    /**
     * 获取所有知识文档（分页）
     */
    List<KnowledgeDoc> listAll();

    /**
     * 按分类获取知识文档
     */
    List<KnowledgeDoc> listByCategory(String category);

    /**
     * 新增知识文档
     */
    KnowledgeDoc addDoc(KnowledgeDoc doc);

    /**
     * 更新知识文档
     */
    KnowledgeDoc updateDoc(KnowledgeDoc doc);

    /**
     * 删除知识文档
     */
    boolean deleteDoc(Long id);
}
