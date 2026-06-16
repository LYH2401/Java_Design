package com.campus.controller;

import com.campus.dto.ChatRequest;
import com.campus.dto.R;
import com.campus.dto.ResultCode;
import com.campus.service.AgentService;
import com.campus.service.ConversationService;
import com.campus.service.impl.AgentServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Agent 控制器
 * 提供基于 Function Calling 的智能对话 API
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final AgentServiceImpl agentServiceImpl;
    private final ConversationService conversationService;

    public AgentController(AgentService agentService,
                           AgentServiceImpl agentServiceImpl,
                           ConversationService conversationService) {
        this.agentService = agentService;
        this.agentServiceImpl = agentServiceImpl;
        this.conversationService = conversationService;
    }

    /**
     * Agent 流式对话（SSE）
     * 模型自动判断是否需要调用工具，工具结果自动注入后续对话
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentChat(@RequestBody ChatRequest request) {
        Long conversationId = getOrCreateConversationId(request);
        String model = request.getModel() != null ? request.getModel() : "dashscope";
        log.info("Agent 对话请求: conversationId={}, message={}, model={}", conversationId, request.getMessage(), model);

        return agentService.agentChat(conversationId, request.getMessage(), model)
                .onErrorResume(e -> {
                    log.error("Agent 流式输出异常: {}", e.getMessage(), e);
                    return Flux.just("[错误] " + e.getMessage());
                });
    }

    /**
     * Agent 对话 + RAG 知识增强（SSE）
     * 先检索知识库获取相关文档，再交给 Agent 进行工具调用和回复
     */
    @PostMapping(value = "/chat/with-rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentChatWithRag(@RequestBody ChatRequest request) {
        Long conversationId = getOrCreateConversationId(request);
        String model = request.getModel() != null ? request.getModel() : "dashscope";
        log.info("RAG+Agent 对话请求: conversationId={}, message={}, model={}", conversationId, request.getMessage(), model);

        return agentServiceImpl.agentChatWithRag(conversationId, request.getMessage(), model)
                .onErrorResume(e -> {
                    log.error("RAG+Agent 流式输出异常: {}", e.getMessage(), e);
                    return Flux.just("[错误] " + e.getMessage());
                });
    }

    /**
     * 获取已注册的工具列表
     */
    @GetMapping("/tools")
    public R<Map<String, Object>> getTools() {
        Map<String, Object> tools = agentService.getTools();
        return R.ok(tools);
    }

    // ==================== 私有辅助方法 ====================

    private Long getOrCreateConversationId(ChatRequest request) {
        if (request.getConversationId() != null && request.getConversationId() > 0) {
            return request.getConversationId();
        }
        // 无 conversationId 时自动创建新会话
        return conversationService.createConversation(1L, "Agent 对话").getId();
    }
}
