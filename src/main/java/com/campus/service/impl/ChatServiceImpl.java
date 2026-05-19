package com.campus.service.impl;

import com.campus.entity.AlertLog;
import com.campus.entity.KnowledgeDoc;
import com.campus.entity.Message;
import com.campus.repository.AlertLogMapper;
import com.campus.repository.MessageMapper;
import com.campus.service.ChatService;
import com.campus.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** 历史消息最大条数 */
    private static final int MAX_HISTORY = 20;
    /** RAG 检索 topK */
    private static final int RAG_TOP_K = 5;

    private final ChatClient chatClient;
    private final MessageMapper messageMapper;
    private final AlertLogMapper alertLogMapper;
    private final KnowledgeService knowledgeService;

    public ChatServiceImpl(ChatClient chatClient,
                           MessageMapper messageMapper,
                           AlertLogMapper alertLogMapper,
                           KnowledgeService knowledgeService) {
        this.chatClient = chatClient;
        this.messageMapper = messageMapper;
        this.alertLogMapper = alertLogMapper;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public Flux<String> chatStream(Long conversationId, String userMessage) {
        // 1. 保存用户消息
        saveMessage(conversationId, "USER", userMessage);

        // 2. 加载历史消息作为上下文
        String historyContext = buildHistoryContext(conversationId);

        // 3. 构建完整 Prompt
        String prompt = buildPrompt(historyContext, userMessage);

        // 4. 调用 ChatClient 流式输出
        StringBuilder fullResponse = new StringBuilder();

        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    // 5. 流结束后保存 AI 回复
                    String response = fullResponse.toString();
                    saveMessage(conversationId, "ASSISTANT", response);
                    log.debug("对话完成: conversationId={}, response长度={}", conversationId, response.length());
                })
                .doOnError(e -> {
                    log.error("AI 调用异常: conversationId={}", conversationId, e);
                    saveAlert("AI_ERROR", "AI 调用失败: " + e.getMessage());
                });
    }

    @Override
    public String chat(Long conversationId, String userMessage) {
        // 1. 保存用户消息
        saveMessage(conversationId, "USER", userMessage);

        // 2. 加载历史消息
        String historyContext = buildHistoryContext(conversationId);

        // 3. 构建 Prompt
        String prompt = buildPrompt(historyContext, userMessage);

        // 4. 同步调用
        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            // 5. 保存 AI 回复
            saveMessage(conversationId, "ASSISTANT", response);
            return response;
        } catch (Exception e) {
            log.error("AI 调用异常: conversationId={}", conversationId, e);
            saveAlert("AI_ERROR", "AI 调用失败: " + e.getMessage());
            return "抱歉，AI 服务暂时不可用，请稍后重试。";
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 保存消息到数据库
     */
    private void saveMessage(Long conversationId, String role, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    /**
     * 构建历史消息上下文
     */
    private String buildHistoryContext(Long conversationId) {
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime)
                        .last("LIMIT " + MAX_HISTORY)
        );

        if (history.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("以下是对话历史：\n");
        for (Message msg : history) {
            String prefix = "USER".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(prefix).append("：").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建最终 Prompt（含 RAG 知识上下文）
     */
    private String buildPrompt(String historyContext, String userMessage) {
        // 1. 检索 RAG 知识上下文
        String ragContext = buildRagContext(userMessage);

        StringBuilder sb = new StringBuilder();

        // 2. 先放 RAG 知识（如有）
        if (!ragContext.isEmpty()) {
            sb.append(ragContext).append("\n\n");
        }

        // 3. 再放历史对话
        if (!historyContext.isEmpty()) {
            sb.append(historyContext).append("\n");
        }

        // 4. 最后放用户最新问题
        sb.append("用户最新问题：").append(userMessage);
        sb.append("\n请基于以上信息回答用户的最新问题。");

        String finalPrompt = sb.toString();
        log.debug("RAG Prompt 长度: {} 字符, 含RAG上下文: {}", finalPrompt.length(), !ragContext.isEmpty());
        return finalPrompt;
    }

    /**
     * 构建 RAG 知识上下文
     * 从知识库中检索相关文档并格式化为 Prompt 片段
     */
    private String buildRagContext(String userMessage) {
        try {
            List<KnowledgeDoc> docs = knowledgeService.search(userMessage, RAG_TOP_K);
            if (docs.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【参考知识库（请基于以下校园知识回答，若与问题不相关则忽略）】\n");

            for (int i = 0; i < docs.size(); i++) {
                KnowledgeDoc doc = docs.get(i);
                sb.append("知识").append(i + 1).append("：");
                if (doc.getCategory() != null && !doc.getCategory().isEmpty()) {
                    sb.append("[").append(doc.getCategory()).append("] ");
                }
                sb.append(doc.getTitle()).append(" - ").append(doc.getContent()).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("RAG 知识检索异常", e);
            saveAlert("RAG_ERROR", "知识库检索失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 记录告警日志
     */
    private void saveAlert(String alertType, String alertMessage) {
        AlertLog alert = new AlertLog();
        alert.setAlertType(alertType);
        alert.setMessage(alertMessage);
        alert.setCreateTime(LocalDateTime.now());
        alertLogMapper.insert(alert);
    }
}
