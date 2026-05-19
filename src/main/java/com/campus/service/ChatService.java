package com.campus.service;

import reactor.core.publisher.Flux;

public interface ChatService {

    /**
     * 流式对话
     * @param conversationId 会话 ID
     * @param userMessage    用户消息
     * @return 流式 AI 回复
     */
    Flux<String> chatStream(Long conversationId, String userMessage);

    /**
     * 非流式对话
     * @param conversationId 会话 ID
     * @param userMessage    用户消息
     * @return AI 回复全文
     */
    String chat(Long conversationId, String userMessage);
}
