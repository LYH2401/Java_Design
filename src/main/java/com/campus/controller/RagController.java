package com.campus.controller;

import com.campus.dto.ChatRequest;
import com.campus.dto.R;
import com.campus.service.ConversationService;
import com.campus.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * RAG 增强问答控制器
 * 提供基于向量检索 + ChatClient 的智能流式问答接口
 */
@RestController
@RequestMapping("/api/rag")
@Tag(name = "RAG 增强问答", description = "基于校园知识库的向量检索增强生成问答")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagService ragService;
    private final ConversationService conversationService;

    public RagController(RagService ragService, ConversationService conversationService) {
        this.ragService = ragService;
        this.conversationService = conversationService;
    }

    // ==================== 核心接口 ====================

    /**
     * RAG 流式问答（SSE）
     * 从 JSON Body 接收 conversationId 和 message，支持对话历史上下文
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "RAG 流式问答",
            description = "传入 conversationId 和 message，经向量检索→置信度门控→增强Prompt→ChatClient流式输出。返回 SSE 流。")
    public Flux<String> ragChat(@RequestBody ChatRequest request) {
        Long conversationId = getOrCreateConversationId(request);
        request.setConversationId(conversationId);
        log.info("RAG 流式问答请求: conversationId={}, message=\"{}\"",
                conversationId, request.getMessage().substring(0, Math.min(80, request.getMessage().length())));
        return ragService.answerWithContext(request);
    }

    /**
     * 获取检索来源文档
     * 返回最近一次相似度检索的文档来源（含相似度信息）
     */
    @GetMapping("/sources")
    @Operation(summary = "获取检索来源",
            description = "根据查询文本返回向量检索到的知识文档来源，包含标题、分类、内容摘要和相似度")
    public R<List<Map<String, Object>>> getSources(
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "返回条数，默认5") @RequestParam(defaultValue = "5") int topK) {
        List<Document> docs = ragService.getSources(query, topK);

        List<Map<String, Object>> result = docs.stream().map(doc -> {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("title", doc.getMetadata().getOrDefault("title", "未知"));
            item.put("category", doc.getMetadata().getOrDefault("category", "其他"));
            item.put("snippet", doc.getText() != null
                    ? doc.getText().substring(0, Math.min(doc.getText().length(), 200))
                    : "");
            item.put("similarity", extractSimilarityDisplay(doc));
            return item;
        }).collect(java.util.stream.Collectors.toList());

        return R.ok(result);
    }

    /**
     * 重新加载知识库
     * 清空向量库 → 重新加载文档 → 分块 → 向量化存储
     */
    @PostMapping("/reload")
    @Operation(summary = "重新加载知识库",
            description = "清空当前向量库，重新从 resources/knowledge/ 加载所有 .txt 文档，分块后向量化存储。返回重新加载的块数量。")
    public R<Map<String, Object>> reload() {
        log.info("收到知识库重载请求...");
        int count = ragService.reloadKnowledge();

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("chunks", count);
        data.put("status", count >= 0 ? "success" : "failed");

        if (count >= 0) {
            data.put("message", "知识库重载成功，共 " + count + " 个知识块");
            return R.ok(data);
        } else {
            data.put("message", "知识库重载失败，请检查日志");
            return R.fail("知识库重载失败");
        }
    }

    /**
     * 向量相似度检索（原始结果）
     * 不经过置信度门控，直接返回向量检索结果
     */
    @GetMapping("/search")
    @Operation(summary = "向量相似度检索",
            description = "直接进行向量相似度检索，不经过置信度门控，返回原始检索结果。可用于调试和前端预览。")
    public R<List<Map<String, Object>>> search(
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "返回条数，默认3") @RequestParam(defaultValue = "3") int topK) {
        List<Document> docs = ragService.search(query, topK);

        List<Map<String, Object>> result = docs.stream().map(doc -> {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("title", doc.getMetadata().getOrDefault("title", "未知"));
            item.put("category", doc.getMetadata().getOrDefault("category", "其他"));
            item.put("snippet", doc.getText() != null
                    ? doc.getText().substring(0, Math.min(doc.getText().length(), 300))
                    : "");
            item.put("similarity", extractSimilarityDisplay(doc));
            item.put("fullText", doc.getText());
            return item;
        }).collect(java.util.stream.Collectors.toList());

        return R.ok(result);
    }

    // ==================== 私有辅助方法 ====================

    private Long getOrCreateConversationId(ChatRequest request) {
        if (request.getConversationId() != null && request.getConversationId() > 0) {
            return request.getConversationId();
        }
        return conversationService.createConversation(1L, "RAG 对话").getId();
    }

    /**
     * 从 Document metadata 中提取相似度用于展示
     */
    private String extractSimilarityDisplay(Document doc) {
        if (doc.getMetadata() == null) return "N/A";

        String[] keys = {"similarity", "score", "distance", "cosine_similarity"};
        for (String key : keys) {
            Object val = doc.getMetadata().get(key);
            if (val != null) {
                try {
                    double sim = Double.parseDouble(val.toString());
                    if ("distance".equals(key)) {
                        sim = 1.0 / (1.0 + sim);
                    }
                    return String.format("%.4f", sim);
                } catch (NumberFormatException ignored) {
                    return val.toString();
                }
            }
        }
        return "N/A";
    }
}
