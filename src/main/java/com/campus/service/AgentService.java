package com.campus.service;

import com.campus.dto.ChatRequest;

import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * Agent 对话服务
 * 协调 ChatClient + Tool 的自动工具选择与调用流程
 */
public interface AgentService {

    /**
     * Agent 流式对话（SSE）
     * @param request 包含 conversationId, message, model, apiKey, baseUrl
     * @return SSE 流
     */
    Flux<String> agentChat(ChatRequest request);

    /**
     * Agent 流式对话（SSE，兼容旧接口）
     */
    Flux<String> agentChat(Long conversationId, String userMessage, String model);

    /**
     * 获取已注册的工具列表
     */
    Map<String, Object> getTools();
}
