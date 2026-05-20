package com.campus.service;

import java.util.Map;

/**
 * RAG 效果对比评估服务接口
 * 提供 RAG vs Non-RAG 对比回答以及 RAG 使用统计
 */
public interface RagEvaluationService {

    /**
     * 对比 RAG 与纯 LLM 对同一问题的回答
     * 分别使用 RAG 增强路径和直接 LLM 路径回答，返回对比结果
     *
     * @param question 用户问题
     * @return 包含 ragAnswer、nonRagAnswer、retrievedDocs、similarity 等字段的对比结果
     */
    Map<String, Object> compareRagVsNonRag(String question);

    /**
     * 获取 RAG 使用统计数据
     * 包括：总评估次数、RAG 命中率、平均最高相似度、RAG 启用/拒绝次数等
     *
     * @return 统计数据 Map
     */
    Map<String, Object> getStats();
}
