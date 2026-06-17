package com.campus.service;

import com.campus.dto.ChatRequest;
import reactor.core.publisher.Flux;

/**
 * 问题求解服务
 * 独立的解题助手，使用分步推理策略帮助学生解决问题
 */
public interface SolverService {

    /**
     * 流式问题求解（支持自定义 API 配置）
     * @param request 包含 conversationId, message, model, apiKey, baseUrl
     * @return 流式 AI 回复
     */
    Flux<String> solveStream(ChatRequest request);

    /**
     * 流式问题求解（兼容旧接口）
     */
    Flux<String> solveStream(Long conversationId, String userMessage, String model);
}
