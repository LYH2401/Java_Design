package com.campus.service;

import reactor.core.publisher.Flux;

/**
 * 问题求解服务
 * 独立的解题助手，使用分步推理策略帮助学生解决问题
 */
public interface SolverService {

    /**
     * 流式问题求解
     * @param conversationId 会话 ID
     * @param userMessage    用户问题
     * @param model          模型选择（dashscope / deepseek）
     * @return 流式 AI 回复
     */
    Flux<String> solveStream(Long conversationId, String userMessage, String model);
}
