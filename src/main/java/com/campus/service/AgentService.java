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
     * 模型根据用户输入自动决定是否调用工具，工具结果自动注入后续对话，
     * 最终回复以 SSE 流式输出。
     *
     * @param conversationId 会话 ID
     * @param userMessage    用户输入
     * @return SSE 流（Flux<String>）
     */
    Flux<String> agentChat(Long conversationId, String userMessage);

    /**
     * 获取已注册的工具列表
     *
     * @return 工具名称及描述
     */
    Map<String, Object> getTools();
}
