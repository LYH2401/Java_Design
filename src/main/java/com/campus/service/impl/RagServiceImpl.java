package com.campus.service.impl;

import com.campus.config.DynamicChatClientFactory;
import com.campus.dto.ChatRequest;
import com.campus.entity.Message;
import com.campus.entity.RagEvaluation;
import com.campus.repository.MessageMapper;
import com.campus.repository.RagEvaluationMapper;
import com.campus.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RAG 增强问答服务实现
 * 流程：向量检索(3条) → 置信度门控 → 加载历史 → 构建增强Prompt → ChatClient流式 → 记录rag_evaluation
 */
@Service
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    /** RAG 检索 topK */
    private static final int RAG_TOP_K = 3;
    /** 分块大小 */
    private static final int CHUNK_SIZE = 500;
    /** 块间重叠 */
    private static final int OVERLAP = 50;
    /** 历史轮次限制 */
    private static final int MAX_HISTORY_ROUNDS = 5;
    /** 单条消息最大字符数（约 2000 tokens） */
    private static final int MAX_MESSAGE_CHARS = 4000;

    private final ChatClient chatClient;
    private final DynamicChatClientFactory dynamicChatClientFactory;
    private final EmbeddingService embeddingService;
    private final DocumentService documentService;
    private final ConfidenceGate confidenceGate;
    private final RagEvaluationMapper ragEvaluationMapper;
    private final SimpleVectorStore vectorStore;
    private final ConversationService conversationService;
    private final MessageMapper messageMapper;

    public RagServiceImpl(ChatClient chatClient,
                          DynamicChatClientFactory dynamicChatClientFactory,
                          EmbeddingService embeddingService,
                          DocumentService documentService,
                          ConfidenceGate confidenceGate,
                          RagEvaluationMapper ragEvaluationMapper,
                          SimpleVectorStore vectorStore,
                          ConversationService conversationService,
                          MessageMapper messageMapper) {
        this.chatClient = chatClient;
        this.dynamicChatClientFactory = dynamicChatClientFactory;
        this.embeddingService = embeddingService;
        this.documentService = documentService;
        this.confidenceGate = confidenceGate;
        this.ragEvaluationMapper = ragEvaluationMapper;
        this.vectorStore = vectorStore;
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
    }

    @Override
    public Flux<String> answerWithContext(ChatRequest request) {
        Long rawConversationId = request.getConversationId();
        if (rawConversationId == null || rawConversationId <= 0) {
            rawConversationId = conversationService.createConversation(1L, "RAG 对话").getId();
        }
        final Long conversationId = rawConversationId;
        final String userMessage = request.getMessage();
        final String model = request.getModel() != null ? request.getModel() : "dashscope";

        saveMessage(conversationId, "USER", userMessage);
        tryAutoTitle(conversationId, userMessage);

        String historyContext = buildHistoryContext(conversationId);

        ChatClient client;
        if (request.hasCustomApiConfig()) {
            client = dynamicChatClientFactory.createChatClient(
                    request.getApiKey(), request.getBaseUrl(), model);
        } else {
            client = chatClient;
        }

        String truncated = truncateMessage(userMessage);
        log.info("RAG 问答开始: query=\"{}\"", truncated.substring(0, Math.min(80, truncated.length())));

        List<Document> retrievedDocs;
        try {
            retrievedDocs = embeddingService.similaritySearch(truncated, RAG_TOP_K);
        } catch (Exception e) {
            log.error("RAG 向量检索异常", e);
            return Flux.just("抱歉，知识库检索服务暂时不可用，请稍后重试。");
        }

        double topSimilarity = retrievedDocs.isEmpty() ? 0.0
                : confidenceGate.extractTopSimilarity(retrievedDocs);
        boolean isConfident = confidenceGate.isConfident(retrievedDocs);

        if (!isConfident) {
            log.info("RAG 置信度不足: topSimilarity={}, threshold={} → 拒绝基于知识库回答",
                    String.format("%.4f", topSimilarity), confidenceGate.getThreshold());
            saveRagEvaluation(userMessage, retrievedDocs, topSimilarity, false, null);
            return Flux.just("抱歉，我暂时没有找到与该问题相关的校园知识。建议您：\n"
                    + "1. 尝试换一种表述方式提问\n"
                    + "2. 联系学校相关部门获取准确信息\n"
                    + "3. 访问学校官网查询最新资讯");
        }

        String enhancedPrompt = buildEnhancedPrompt(truncated, retrievedDocs, historyContext);
        StringBuilder fullResponse = new StringBuilder();

        return client.prompt()
                .user(enhancedPrompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    String response = fullResponse.toString();
                    saveMessage(conversationId, "ASSISTANT", response);
                    saveRagEvaluation(userMessage, retrievedDocs, topSimilarity, true, response);
                    log.info("RAG 问答完成: query长度={}, 检索文档数={}, 相似度={}, 回复长度={}",
                            userMessage.length(), retrievedDocs.size(),
                            String.format("%.4f", topSimilarity), response.length());
                })
                .doOnError(e -> {
                    log.error("RAG ChatClient 调用异常", e);
                    saveRagEvaluation(userMessage, retrievedDocs, topSimilarity, true, "[ERROR] " + e.getMessage());
                });
    }

    @Override
    public Flux<String> answerWithContext(Long conversationId, String userMessage) {
        // 保存用户消息
        saveMessage(conversationId, "USER", userMessage);

        // 自动标题
        tryAutoTitle(conversationId, userMessage);

        // 加载历史
        String historyContext = buildHistoryContext(conversationId);

        return doAnswerWithContext(conversationId, userMessage, historyContext);
    }

    @Override
    public Flux<String> answerWithContext(String userMessage) {
        return doAnswerWithContext(null, userMessage, null);
    }

    private Flux<String> doAnswerWithContext(Long conversationId, String userMessage, String historyContext) {
        // 截断过长用户消息
        String truncated = truncateMessage(userMessage);
        log.info("RAG 问答开始: query=\"{}\"", truncated.substring(0, Math.min(80, truncated.length())));

        // 1. 向量相似度检索 topK=3
        List<Document> retrievedDocs;
        try {
            retrievedDocs = embeddingService.similaritySearch(truncated, RAG_TOP_K);
        } catch (Exception e) {
            log.error("RAG 向量检索异常", e);
            return Flux.just("抱歉，知识库检索服务暂时不可用，请稍后重试。");
        }

        // 2. 置信度门控
        double topSimilarity = retrievedDocs.isEmpty() ? 0.0
                : confidenceGate.extractTopSimilarity(retrievedDocs);
        boolean isConfident = confidenceGate.isConfident(retrievedDocs);

        if (!isConfident) {
            log.info("RAG 置信度不足: topSimilarity={}, threshold={} → 拒绝基于知识库回答",
                    String.format("%.4f", topSimilarity), confidenceGate.getThreshold());

            saveRagEvaluation(userMessage, retrievedDocs, topSimilarity, false, null);

            return Flux.just("抱歉，我暂时没有找到与该问题相关的校园知识。建议您：\n"
                    + "1. 尝试换一种表述方式提问\n"
                    + "2. 联系学校相关部门获取准确信息\n"
                    + "3. 访问学校官网查询最新资讯");
        }

        // 3. 构建增强 Prompt（含历史上下文）
        String enhancedPrompt = buildEnhancedPrompt(truncated, retrievedDocs, historyContext);

        // 4. 调用 ChatClient 流式输出
        StringBuilder fullResponse = new StringBuilder();

        return chatClient.prompt()
                .user(enhancedPrompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    String response = fullResponse.toString();
                    // 保存 AI 回复
                    if (conversationId != null) {
                        saveMessage(conversationId, "ASSISTANT", response);
                    }
                    // 记录 RAG 评估日志
                    saveRagEvaluation(userMessage, retrievedDocs, topSimilarity, true, response);
                    log.info("RAG 问答完成: query长度={}, 检索文档数={}, 相似度={}, 回复长度={}",
                            userMessage.length(), retrievedDocs.size(),
                            String.format("%.4f", topSimilarity), response.length());
                })
                .doOnError(e -> {
                    log.error("RAG ChatClient 调用异常", e);
                    saveRagEvaluation(userMessage, retrievedDocs, topSimilarity, true, "[ERROR] " + e.getMessage());
                });
    }

    @Override
    public List<Document> getSources(String query, int topK) {
        int k = Math.max(topK, 1);
        log.debug("获取 RAG 检索来源: query=\"{}\", topK={}", query, k);
        return embeddingService.similaritySearch(query, k);
    }

    @Override
    public int reloadKnowledge() {
        log.info("开始重新加载知识库...");
        try {
            List<Document> documents = documentService.loadDocuments();
            if (documents.isEmpty()) {
                log.warn("未找到任何知识文档，知识库重载失败");
                return 0;
            }
            List<Document> chunks = documentService.splitDocuments(documents, CHUNK_SIZE, OVERLAP);
            log.info("文档分块完成: {} 篇文档 → {} 个块", documents.size(), chunks.size());
            embeddingService.embedAndStore(chunks);
            int size = embeddingService.getStoreSize();
            log.info("知识库重载完成: 共 {} 个向量块", size);
            return size;
        } catch (Exception e) {
            log.error("知识库重载失败", e);
            return -1;
        }
    }

    @Override
    public List<Document> search(String query, int topK) {
        int k = Math.max(topK, 1);
        return embeddingService.similaritySearch(query, k);
    }

    // ==================== 私有辅助方法 ====================

    private void saveMessage(Long conversationId, String role, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private void tryAutoTitle(Long conversationId, String userMessage) {
        try {
            long count = messageMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>()
                            .eq(Message::getConversationId, conversationId)
            );
            if (count == 1) {
                conversationService.updateTitle(conversationId, userMessage);
            }
        } catch (Exception e) {
            log.warn("自动更新标题失败: conversationId={}", conversationId, e);
        }
    }

    private String buildHistoryContext(Long conversationId) {
        List<Message> history = conversationService.loadRecentRounds(conversationId, MAX_HISTORY_ROUNDS);
        if (history.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("【对话历史】\n");
        for (Message msg : history) {
            String prefix = "USER".equals(msg.getRole()) ? "用户" : "助手";
            String content = truncateMessage(msg.getContent());
            sb.append(prefix).append("：").append(content).append("\n");
        }
        return sb.toString();
    }

    private String truncateMessage(String content) {
        if (content == null || content.length() <= MAX_MESSAGE_CHARS) return content;
        return content.substring(0, MAX_MESSAGE_CHARS) + "...[消息过长已截断]";
    }

    /**
     * 构建 RAG 增强 Prompt
     */
    private String buildEnhancedPrompt(String userMessage, List<Document> docs, String historyContext) {
        StringBuilder sb = new StringBuilder();

        sb.append("请严格基于以下校园知识库内容回答用户问题。\n");
        sb.append("如果知识库内容足以回答问题，请直接给出准确答案；");
        sb.append("如果知识库内容不完全覆盖问题，请在回答中说明「根据已有知识」并补充建议。\n\n");

        sb.append("【校园知识库参考内容】\n");
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            sb.append("--- 参考知识 ").append(i + 1).append(" ---\n");

            String title = String.valueOf(doc.getMetadata().getOrDefault("title", "未知"));
            String category = String.valueOf(doc.getMetadata().getOrDefault("category", "其他"));
            sb.append("标题：").append(title);
            sb.append("（分类：").append(category).append("）\n");
            sb.append("内容：").append(doc.getText()).append("\n\n");
        }

        if (historyContext != null && !historyContext.isEmpty()) {
            sb.append(historyContext).append("\n");
        }

        sb.append("【用户问题】\n");
        sb.append(userMessage);
        sb.append("\n\n请用中文给出简洁、准确、友好的回答。");

        String prompt = sb.toString();
        log.debug("RAG 增强 Prompt 构建完成: 长度={} 字符, 含{}篇参考知识", prompt.length(), docs.size());
        return prompt;
    }

    /**
     * 保存 RAG 评估记录到数据库
     */
    private void saveRagEvaluation(String userMessage, List<Document> retrievedDocs,
                                   double topSimilarity, boolean ragEnabled, String responseContent) {
        try {
            RagEvaluation eval = new RagEvaluation();
            eval.setMessageId(null);
            eval.setRagEnabled(ragEnabled ? 1 : 0);
            eval.setRetrievedDocs(formatRetrievedDocs(retrievedDocs));
            eval.setTopSimilarity(confidenceGate.toBigDecimal(topSimilarity));
            eval.setResponseContent(responseContent != null
                    ? responseContent.substring(0, Math.min(responseContent.length(), 2000))
                    : null);
            eval.setCreateTime(LocalDateTime.now());
            ragEvaluationMapper.insert(eval);
            log.debug("RAG 评估记录已保存: id={}, ragEnabled={}, topSimilarity={}",
                    eval.getId(), eval.getRagEnabled(), eval.getTopSimilarity());
        } catch (Exception e) {
            log.error("保存 RAG 评估记录失败", e);
        }
    }

    /**
     * 将检索文档列表格式化为 JSON 字符串供存储
     */
    private String formatRetrievedDocs(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < docs.size(); i++) {
            if (i > 0) sb.append(",");
            Document doc = docs.get(i);
            String title = escapeJson(String.valueOf(doc.getMetadata().getOrDefault("title", "")));
            String category = escapeJson(String.valueOf(doc.getMetadata().getOrDefault("category", "")));
            String text = escapeJson(doc.getText() != null
                    ? doc.getText().substring(0, Math.min(doc.getText().length(), 100))
                    : "");
            sb.append("{\"title\":\"").append(title).append("\",");
            sb.append("\"category\":\"").append(category).append("\",");
            sb.append("\"snippet\":\"").append(text).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 简易 JSON 字符串转义
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
