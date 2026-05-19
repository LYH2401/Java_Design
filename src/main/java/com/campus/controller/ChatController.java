package com.campus.controller;

import com.campus.dto.ChatRequest;
import com.campus.dto.R;
import com.campus.entity.Conversation;
import com.campus.entity.Message;
import com.campus.repository.MessageMapper;
import com.campus.service.ChatService;
import com.campus.service.ConversationService;
import com.campus.service.FallbackChatService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "智能对话", description = "AI 对话与聊天管理接口")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final FallbackChatService fallbackChatService;
    private final ConversationService conversationService;
    private final MessageMapper messageMapper;

    /** 默认用户 ID（后续接入登录后替换） */
    private static final Long DEFAULT_USER_ID = 1L;

    public ChatController(ChatService chatService,
                          FallbackChatService fallbackChatService,
                          ConversationService conversationService,
                          MessageMapper messageMapper) {
        this.chatService = chatService;
        this.fallbackChatService = fallbackChatService;
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
    }

    // ==================== 流式对话 ====================

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话（SSE）", description = "发送消息并以 Server-Sent Events 流式返回 AI 回复，完成后发送 [DONE]")
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        Long conversationId = getOrCreateConversationId(request);

        return chatService.chatStream(conversationId, request.getMessage())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .timeout(Duration.ofSeconds(120))
                .concatWithValues(
                        ServerSentEvent.<String>builder()
                                .data("[DONE]")
                                .build()
                )
                .onErrorResume(e -> {
                    log.error("SSE 流异常, 触发降级", e);
                    String fallback = fallbackChatService.getFallbackResponse(request.getMessage());
                    return Flux.just(
                            ServerSentEvent.<String>builder().data(fallback).build(),
                            ServerSentEvent.<String>builder().data("[DONE]").build()
                    );
                });
    }

    // ==================== 普通对话 ====================

    @PostMapping("/chat")
    @Operation(summary = "普通对话", description = "发送消息并返回完整 AI 回复")
    public R<String> chat(@RequestBody ChatRequest request) {
        Long conversationId = getOrCreateConversationId(request);

        try {
            String response = chatService.chat(conversationId, request.getMessage());
            return R.ok(response);
        } catch (Exception e) {
            log.error("AI 调用失败, 触发降级", e);
            String fallback = fallbackChatService.getFallbackResponse(request.getMessage());
            return R.ok(fallback);
        }
    }

    // ==================== 会话管理 ====================

    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表")
    public R<List<Conversation>> listConversations() {
        List<Conversation> list = conversationService.listConversations(DEFAULT_USER_ID);
        return R.ok(list);
    }

    @PostMapping("/conversations")
    @Operation(summary = "新建会话")
    public R<Conversation> createConversation(
            @Parameter(description = "会话标题（可选）")
            @RequestParam(required = false) String title) {
        Conversation conv = conversationService.createConversation(DEFAULT_USER_ID, title);
        return R.ok(conv);
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "获取会话历史消息")
    public R<List<Message>> getMessages(
            @Parameter(description = "会话 ID") @PathVariable Long id) {
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, id)
                        .orderByAsc(Message::getCreateTime)
        );
        return R.ok(messages);
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除会话（含关联消息）")
    public R<Void> deleteConversation(
            @Parameter(description = "会话 ID") @PathVariable Long id) {
        conversationService.deleteConversation(id);
        return R.ok();
    }

    // ==================== 私有方法 ====================

    /**
     * 获取或创建会话 ID
     */
    private Long getOrCreateConversationId(ChatRequest request) {
        if (request.getConversationId() != null) {
            return request.getConversationId();
        }
        // 自动创建新会话
        Conversation conv = conversationService.createConversation(DEFAULT_USER_ID, "新对话");
        return conv.getId();
    }
}
