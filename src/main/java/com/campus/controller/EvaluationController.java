package com.campus.controller;

import com.campus.dto.R;
import com.campus.service.RagEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RAG 效果评估控制器
 * 提供 RAG vs Non-RAG 对比回答 + 使用统计，为 Python 评估脚本提供数据源
 */
@RestController
@RequestMapping("/api/eval")
@Tag(name = "RAG 效果评估", description = "RAG vs 纯LLM 对比评估及使用统计，可供 Python 脚本分析")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final RagEvaluationService ragEvaluationService;

    public EvaluationController(RagEvaluationService ragEvaluationService) {
        this.ragEvaluationService = ragEvaluationService;
    }

    /**
     * 对比 RAG vs Non-RAG 回答
     * 对同一问题分别走 RAG 增强路径和纯 LLM 路径，返回对比结果
     */
    @GetMapping("/compare")
    @Operation(summary = "RAG vs Non-RAG 对比",
            description = "对同一问题分别走 RAG 增强路径（向量检索+知识注入）和纯 LLM 路径，返回两次回答的完整对比 JSON。"
                    + "可用于人工评估 RAG 效果和 Python 脚本批量分析。")
    public R<Map<String, Object>> compare(
            @Parameter(description = "待评估的问题", required = true, example = "图书馆周末开放时间？")
            @RequestParam String question) {

        if (question == null || question.trim().isEmpty()) {
            return R.fail("问题不能为空");
        }

        log.info("收到 RAG vs Non-RAG 对比请求: question=\"{}\"",
                question.substring(0, Math.min(80, question.length())));

        Map<String, Object> result = ragEvaluationService.compareRagVsNonRag(question);
        return R.ok(result);
    }

    /**
     * 获取 RAG 使用统计数据
     * 包含命中率、平均相似度、总评估次数、最近评估记录等
     */
    @GetMapping("/stats")
    @Operation(summary = "RAG 使用统计",
            description = "返回 RAG 评估统计数据：总评估次数、RAG 命中率、平均/最大/最小相似度、阈值、最近10条评估记录。"
                    + "可供 Python 脚本拉取后生成可视化图表。")
    public R<Map<String, Object>> stats() {
        log.debug("收到 RAG 统计查询请求");
        Map<String, Object> stats = ragEvaluationService.getStats();
        return R.ok(stats);
    }
}
