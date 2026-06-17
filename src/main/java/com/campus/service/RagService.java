package com.campus.service;

import com.campus.dto.ChatRequest;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 增强问答服务接口
 * 结合向量检索 + ChatClient 流式回答，实现带置信度门控的智能问答
 */
public interface RagService {

    /**
     * 基于 RAG 的流式问答（带对话历史和自定义 API 配置）
     *
     * @param request 包含 conversationId, message, model, apiKey, baseUrl
     * @return SSE 流式响应 Flux（纯文本逐token）
     */
    Flux<String> answerWithContext(ChatRequest request);

    /**
     * 基于 RAG 的流式问答（带对话历史）
     *
     * @param conversationId 会话 ID
     * @param userMessage    用户问题
     * @return SSE 流式响应 Flux（纯文本逐token）
     */
    Flux<String> answerWithContext(Long conversationId, String userMessage);

    /**
     * 基于 RAG 的流式问答（无对话历史，兼容旧接口）
     *
     * @param userMessage 用户问题
     * @return SSE 流式响应 Flux（纯文本逐token）
     */
    Flux<String> answerWithContext(String userMessage);

    /**
     * 获取最近一次问答的检索来源文档
     *
     * @param query 查询文本
     * @param topK  返回条数
     * @return 检索到的文档列表（含相似度 meta）
     */
    List<Document> getSources(String query, int topK);

    /**
     * 重新加载知识库（清空向量库 + 重新加载文档 → 分块 → 向量化）
     *
     * @return 重新加载后的知识块数量
     */
    int reloadKnowledge();

    /**
     * 相似度检索（不经过门控，直接返回原始检索结果）
     *
     * @param query 查询文本
     * @param topK  返回条数
     * @return 检索到的文档列表
     */
    List<Document> search(String query, int topK);
}
