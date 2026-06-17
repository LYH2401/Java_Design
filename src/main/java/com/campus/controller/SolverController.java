package com.campus.controller;

import com.campus.dto.ChatRequest;
import com.campus.dto.R;
import com.campus.entity.Conversation;
import com.campus.entity.Message;
import com.campus.service.ConversationService;
import com.campus.service.MessageService;
import com.campus.service.SolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * 问题求解控制器
 * 独立的问题求解功能，使用分步推理策略
 */
@RestController
@RequestMapping("/api/solver")
public class SolverController {

    private static final Logger log = LoggerFactory.getLogger(SolverController.class);

    private static final Long DEFAULT_USER_ID = 1L;

    private final SolverService solverService;
    private final ConversationService conversationService;
    private final MessageService messageService;

    public SolverController(SolverService solverService,
                            ConversationService conversationService,
                            MessageService messageService) {
        this.solverService = solverService;
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    /**
     * 流式问题求解（SSE）
     */
    @PostMapping(value = "/solve", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> solve(@RequestBody ChatRequest request) {
        Long conversationId = getOrCreateConversationId(request);
        String model = request.getModel() != null ? request.getModel() : "dashscope";
        log.info("问题求解请求: conversationId={}, model={}, message={}", conversationId, model, request.getMessage());

        return solverService.solveStream(conversationId, request.getMessage(), model)
                .timeout(Duration.ofSeconds(120))
                .onErrorResume(e -> {
                    log.error("求解异常: {}", e.getMessage(), e);
                    return Flux.just("[错误] " + e.getMessage());
                });
    }

    @GetMapping("/conversations")
    public R<List<Conversation>> listConversations() {
        return R.ok(conversationService.listConversations(DEFAULT_USER_ID));
    }

    @PostMapping("/conversations")
    public R<Conversation> createConversation(
            @RequestParam(required = false, defaultValue = "新问题") String firstMessage,
            @RequestParam(required = false, defaultValue = "NORMAL") String mode) {
        Conversation conv = conversationService.createConversation(DEFAULT_USER_ID, firstMessage, mode);
        return R.ok(conv);
    }

    @GetMapping("/conversations/{id}/messages")
    public R<List<Message>> getMessages(@PathVariable Long id) {
        return R.ok(messageService.listByConversation(id, 1, 50));
    }

    @DeleteMapping("/conversations/{id}")
    public R<Void> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return R.ok();
    }

    private Long getOrCreateConversationId(ChatRequest request) {
        if (request.getConversationId() != null && request.getConversationId() > 0) {
            return request.getConversationId();
        }
        String firstMsg = request.getMessage() != null ? request.getMessage() : "新问题";
        return conversationService.createConversation(DEFAULT_USER_ID, firstMsg).getId();
    }
}
