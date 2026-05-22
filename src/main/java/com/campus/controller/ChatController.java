package com.campus.controller;

import com.campus.dto.ChatRequest;
import com.campus.dto.R;
import com.campus.entity.Conversation;
import com.campus.entity.Message;
import com.campus.service.ChatService;
import com.campus.service.ConversationService;
import com.campus.service.FallbackChatService;
import com.campus.service.MessageService;
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
    private final MessageService messageService;

    /** 默认用户 ID（后续接入登录后替换） */
    private static final Long DEFAULT_USER_ID = 1L;
    /** 默认消息分页大小 */
    private static final int DEFAULT_PAGE_SIZE = 50;

    public ChatController(ChatService chatService,
                          FallbackChatService fallbackChatService,
                          ConversationService conversationService,
                          MessageService messageService) {
        this.chatService = chatService;
        this.fallbackChatService = fallbackChatService;
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    // ==================== 流式对话 ====================

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话（SSE）", description = "发送消息并以 Server-Sent Events 流式返回 AI 回复，完成后发送 [DONE]")
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        Long conversationId = getOrCreateConversationId(request);

        String model = request.getModel() != null ? request.getModel() : "dashscope";
        return chatService.chatStream(conversationId, request.getMessage(), model)
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
    @Operation(summary = "新建会话（自动从首条消息生成标题前20字）")
    public R<Conversation> createConversation(
            @Parameter(description = "首条消息（用于生成标题）")
            @RequestParam(required = false, defaultValue = "新对话") String firstMessage,
            @Parameter(description = "会话模式：NORMAL（保留记录）/ INCOGNITO（无痕）")
            @RequestParam(required = false, defaultValue = "NORMAL") String mode) {
        Conversation conv = conversationService.createConversation(DEFAULT_USER_ID, firstMessage, mode);
        return R.ok(conv);
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "获取会话历史消息（分页）")
    public R<List<Message>> getMessages(
            @Parameter(description = "会话 ID") @PathVariable Long id,
            @Parameter(description = "页码（从1开始）") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "50") int size) {
        List<Message> messages = messageService.listByConversation(id, page, size);
        return R.ok(messages);
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除会话（含关联消息）")
    public R<Void> deleteConversation(
            @Parameter(description = "会话 ID") @PathVariable Long id) {
        conversationService.deleteConversation(id);
        return R.ok();
    }

    // ==================== 会话记忆管理 ====================

    @GetMapping("/conversations/{id}/rounds")
    @Operation(summary = "加载最近 N 轮对话历史", description = "按轮次（1轮=用户+助手）加载历史消息")
    public R<List<Message>> loadRecentRounds(
            @Parameter(description = "会话 ID") @PathVariable Long id,
            @Parameter(description = "轮次数（默认10）") @RequestParam(defaultValue = "10") int rounds) {
        List<Message> messages = conversationService.loadRecentRounds(id, rounds);
        return R.ok(messages);
    }

    @PostMapping("/conversations/{id}/title")
    @Operation(summary = "更新会话标题")
    public R<Void> updateTitle(
            @Parameter(description = "会话 ID") @PathVariable Long id,
            @Parameter(description = "新标题文本") @RequestParam String title) {
        conversationService.updateTitle(id, title);
        return R.ok();
    }

    @DeleteMapping("/conversations/cleanup")
    @Operation(summary = "清理过期会话", description = "删除 N 天前创建的会话及关联消息")
    public R<Integer> cleanExpiredConversations(
            @Parameter(description = "保留天数（默认30）") @RequestParam(defaultValue = "30") int retentionDays) {
        int count = conversationService.cleanExpiredConversations(retentionDays);
        return R.ok(count);
    }

    // ==================== 私有方法 ====================

    /**
     * 获取或创建会话 ID
     */
    private Long getOrCreateConversationId(ChatRequest request) {
        if (request.getConversationId() != null) {
            return request.getConversationId();
        }
        // 自动创建新会话（从首条消息生成标题）
        String firstMsg = request.getMessage() != null ? request.getMessage() : "新对话";
        Conversation conv = conversationService.createConversation(DEFAULT_USER_ID, firstMsg);
        return conv.getId();
    }
}
