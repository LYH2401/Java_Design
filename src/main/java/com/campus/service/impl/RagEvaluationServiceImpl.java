package com.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.entity.RagEvaluation;
import com.campus.repository.RagEvaluationMapper;
import com.campus.service.ConfidenceGate;
import com.campus.service.EmbeddingService;
import com.campus.service.RagEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 效果对比评估服务实现
 * 提供 RAG vs Non-RAG 对比回答 + RAG 使用统计
 */
@Service
public class RagEvaluationServiceImpl implements RagEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationServiceImpl.class);

    /** RAG 检索 topK */
    private static final int RAG_TOP_K = 3;

    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final ConfidenceGate confidenceGate;
    private final RagEvaluationMapper ragEvaluationMapper;

    public RagEvaluationServiceImpl(ChatClient chatClient,
                                    EmbeddingService embeddingService,
                                    ConfidenceGate confidenceGate,
                                    RagEvaluationMapper ragEvaluationMapper) {
        this.chatClient = chatClient;
        this.embeddingService = embeddingService;
        this.confidenceGate = confidenceGate;
        this.ragEvaluationMapper = ragEvaluationMapper;
    }

    @Override
    public Map<String, Object> compareRagVsNonRag(String question) {
        log.info("开始 RAG vs Non-RAG 对比评估: question=\"{}\"",
                question.substring(0, Math.min(80, question.length())));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("timestamp", LocalDateTime.now().toString());

        // ========== 1. RAG 路径 ==========
        long ragStart = System.currentTimeMillis();
        String ragAnswer;
        List<Document> retrievedDocs = Collections.emptyList();
        double topSimilarity = 0.0;
        boolean ragHit = false;

        try {
            // 1a. 向量检索
            retrievedDocs = embeddingService.similaritySearch(question, RAG_TOP_K);

            // 1b. 计算相似度
            topSimilarity = retrievedDocs.isEmpty() ? 0.0
                    : confidenceGate.extractTopSimilarity(retrievedDocs);

            // 1c. 置信度判断
            ragHit = confidenceGate.isConfident(retrievedDocs);

            if (ragHit) {
                // 构建增强 Prompt 并调用 ChatClient
                String enhancedPrompt = buildEnhancedPrompt(question, retrievedDocs);
                ragAnswer = chatClient.prompt()
                        .user(enhancedPrompt)
                        .call()
                        .content();
            } else {
                ragAnswer = "[RAG 置信度不足，未基于知识库回答]";
            }
        } catch (Exception e) {
            log.error("RAG 路径评估异常", e);
            ragAnswer = "[RAG 路径异常: " + e.getMessage() + "]";
        }
        long ragElapsed = System.currentTimeMillis() - ragStart;

        // ========== 2. 纯 LLM 路径（无知识库） ==========
        long nonRagStart = System.currentTimeMillis();
        String nonRagAnswer;
        try {
            nonRagAnswer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Non-RAG 路径评估异常", e);
            nonRagAnswer = "[纯 LLM 路径异常: " + e.getMessage() + "]";
        }
        long nonRagElapsed = System.currentTimeMillis() - nonRagStart;

        // ========== 3. 组装结果 ==========
        result.put("ragAnswer", ragAnswer);
        result.put("nonRagAnswer", nonRagAnswer);
        result.put("ragElapsedMs", ragElapsed);
        result.put("nonRagElapsedMs", nonRagElapsed);
        result.put("ragHit", ragHit);
        result.put("topSimilarity", BigDecimal.valueOf(topSimilarity).setScale(4, RoundingMode.HALF_UP));

        // 检索文档摘要
        List<Map<String, Object>> docSummaries = new ArrayList<>();
        for (Document doc : retrievedDocs) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("title", doc.getMetadata().getOrDefault("title", "未知"));
            summary.put("category", doc.getMetadata().getOrDefault("category", "其他"));
            summary.put("snippet", doc.getText() != null
                    ? doc.getText().substring(0, Math.min(doc.getText().length(), 150))
                    : "");
            docSummaries.add(summary);
        }
        result.put("retrievedDocs", docSummaries);

        // 差异分析
        result.put("answerDiff", analyzeDifference(ragAnswer, nonRagAnswer));

        // ========== 4. 保存评估记录 ==========
        saveComparisonRecord(question, retrievedDocs, topSimilarity, ragHit, ragAnswer);

        log.info("RAG vs Non-RAG 对比完成: ragHit={}, 相似度={}, RAG耗时={}ms, Non-RAG耗时={}ms",
                ragHit, String.format("%.4f", topSimilarity), ragElapsed, nonRagElapsed);

        return result;
    }

    @Override
    public Map<String, Object> getStats() {
        log.debug("获取 RAG 使用统计数据");

        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            // 总评估次数
            long totalCount = ragEvaluationMapper.selectCount(null);

            // RAG 启用次数 (rag_enabled = 1)
            long ragEnabledCount = ragEvaluationMapper.selectCount(
                    new LambdaQueryWrapper<RagEvaluation>()
                            .eq(RagEvaluation::getRagEnabled, 1)
            );

            // RAG 拒绝次数 (rag_enabled = 0)
            long ragRejectedCount = ragEvaluationMapper.selectCount(
                    new LambdaQueryWrapper<RagEvaluation>()
                            .eq(RagEvaluation::getRagEnabled, 0)
            );

            // 命中率
            double hitRate = totalCount > 0
                    ? (double) ragEnabledCount / totalCount
                    : 0.0;

            // 平均相似度（仅统计 RAG 启用且有相似度记录的）
            List<RagEvaluation> enabledEvals = ragEvaluationMapper.selectList(
                    new LambdaQueryWrapper<RagEvaluation>()
                            .eq(RagEvaluation::getRagEnabled, 1)
                            .isNotNull(RagEvaluation::getTopSimilarity)
            );

            double avgSimilarity = 0.0;
            double maxSimilarity = 0.0;
            double minSimilarity = 1.0;
            if (!enabledEvals.isEmpty()) {
                double sum = 0.0;
                for (RagEvaluation eval : enabledEvals) {
                    BigDecimal sim = eval.getTopSimilarity();
                    if (sim != null) {
                        double v = sim.doubleValue();
                        sum += v;
                        if (v > maxSimilarity) maxSimilarity = v;
                        if (v < minSimilarity) minSimilarity = v;
                    }
                }
                avgSimilarity = sum / enabledEvals.size();
            }
            if (enabledEvals.isEmpty()) {
                minSimilarity = 0.0;
            }

            // 组装统计结果
            stats.put("totalEvaluations", totalCount);
            stats.put("ragEnabledCount", ragEnabledCount);
            stats.put("ragRejectedCount", ragRejectedCount);
            stats.put("hitRate", BigDecimal.valueOf(hitRate).setScale(4, RoundingMode.HALF_UP));
            stats.put("avgSimilarity", BigDecimal.valueOf(avgSimilarity).setScale(4, RoundingMode.HALF_UP));
            stats.put("maxSimilarity", BigDecimal.valueOf(maxSimilarity).setScale(4, RoundingMode.HALF_UP));
            stats.put("minSimilarity", BigDecimal.valueOf(minSimilarity).setScale(4, RoundingMode.HALF_UP));
            stats.put("threshold", BigDecimal.valueOf(confidenceGate.getThreshold()));

            // 最近10条评估记录摘要
            List<RagEvaluation> recent = ragEvaluationMapper.selectList(
                    new LambdaQueryWrapper<RagEvaluation>()
                            .orderByDesc(RagEvaluation::getCreateTime)
                            .last("LIMIT 10")
            );
            List<Map<String, Object>> recentList = recent.stream().map(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.getId());
                item.put("ragEnabled", e.getRagEnabled());
                item.put("topSimilarity", e.getTopSimilarity());
                item.put("createTime", e.getCreateTime() != null ? e.getCreateTime().toString() : null);
                // 截取回复前100字符
                String resp = e.getResponseContent();
                item.put("responsePreview", resp != null
                        ? resp.substring(0, Math.min(resp.length(), 100))
                        : null);
                return item;
            }).collect(Collectors.toList());
            stats.put("recentEvaluations", recentList);

        } catch (Exception e) {
            log.error("获取 RAG 统计数据异常", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建 RAG 增强 Prompt
     */
    private String buildEnhancedPrompt(String question, List<Document> docs) {
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
            sb.append("标题：").append(title).append("（分类：").append(category).append("）\n");
            sb.append("内容：").append(doc.getText()).append("\n\n");
        }
        sb.append("【用户问题】\n").append(question);
        sb.append("\n\n请用中文给出简洁、准确、友好的回答。");
        return sb.toString();
    }

    /**
     * 简单分析两个回答的差异
     */
    private Map<String, Object> analyzeDifference(String ragAnswer, String nonRagAnswer) {
        Map<String, Object> diff = new LinkedHashMap<>();

        int ragLen = ragAnswer != null ? ragAnswer.length() : 0;
        int nonRagLen = nonRagAnswer != null ? nonRagAnswer.length() : 0;

        diff.put("ragLength", ragLen);
        diff.put("nonRagLength", nonRagLen);
        diff.put("lengthRatio", nonRagLen > 0
                ? BigDecimal.valueOf((double) ragLen / nonRagLen).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // 简单词汇重叠率
        if (ragAnswer != null && nonRagAnswer != null) {
            Set<String> ragWords = tokenizeSimple(ragAnswer);
            Set<String> nonRagWords = tokenizeSimple(nonRagAnswer);

            Set<String> intersection = new HashSet<>(ragWords);
            intersection.retainAll(nonRagWords);

            Set<String> union = new HashSet<>(ragWords);
            union.addAll(nonRagWords);

            double jaccard = union.isEmpty() ? 0.0
                    : (double) intersection.size() / union.size();
            diff.put("jaccardSimilarity", BigDecimal.valueOf(jaccard).setScale(4, RoundingMode.HALF_UP));
            diff.put("ragUniqueWords", ragWords.size());
            diff.put("nonRagUniqueWords", nonRagWords.size());
            diff.put("sharedWords", intersection.size());
        }

        return diff;
    }

    /**
     * 简易中文分词（提取连续汉字作为特征）
     */
    private Set<String> tokenizeSimple(String text) {
        Set<String> tokens = new HashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,}").matcher(text);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    /**
     * 保存对比评估记录到 rag_evaluation 表
     */
    private void saveComparisonRecord(String question, List<Document> retrievedDocs,
                                      double topSimilarity, boolean ragHit, String ragAnswer) {
        try {
            RagEvaluation eval = new RagEvaluation();
            eval.setMessageId(null);
            eval.setRagEnabled(ragHit ? 1 : 0);
            eval.setRetrievedDocs(formatDocsJson(retrievedDocs));
            eval.setTopSimilarity(confidenceGate.toBigDecimal(topSimilarity));
            eval.setResponseContent(ragAnswer != null
                    ? ragAnswer.substring(0, Math.min(ragAnswer.length(), 2000))
                    : null);
            eval.setCreateTime(LocalDateTime.now());
            ragEvaluationMapper.insert(eval);
            log.debug("对比评估记录已保存: id={}", eval.getId());
        } catch (Exception e) {
            log.error("保存对比评估记录失败", e);
        }
    }

    private String formatDocsJson(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < docs.size(); i++) {
            if (i > 0) sb.append(",");
            Document doc = docs.get(i);
            String title = escapeJson(String.valueOf(doc.getMetadata().getOrDefault("title", "")));
            String category = escapeJson(String.valueOf(doc.getMetadata().getOrDefault("category", "")));
            String snippet = escapeJson(doc.getText() != null
                    ? doc.getText().substring(0, Math.min(doc.getText().length(), 100))
                    : "");
            sb.append("{\"title\":\"").append(title).append("\",");
            sb.append("\"category\":\"").append(category).append("\",");
            sb.append("\"snippet\":\"").append(snippet).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
