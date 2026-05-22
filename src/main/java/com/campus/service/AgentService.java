package com.campus.service;

import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * Agent 对话服务
 * 协调 ChatClient + Tool 的自动工具选择与调用流程
 */
public interface AgentService {

    /**
     * Agent 流式对话（SSE）
     * @param conversationId 会话 ID
     * @param userMessage    用户输入
     * @param model          模型选择（dashscope / deepseek）
     * @return SSE 流
     */
    Flux<String> agentChat(Long conversationId, String userMessage, String model);

    /**
     * 获取已注册的工具列表
     */
    Map<String, Object> getTools();
}
