package com.campus.controller;

import com.campus.dto.R;
import com.campus.entity.KnowledgeDoc;
import com.campus.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理控制器
 * 提供知识文档的 CRUD 与检索接口
 */
@RestController
@RequestMapping("/api/knowledge")
@Tag(name = "知识库管理", description = "校园知识文档的增删改查与 RAG 检索")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    // ==================== 检索 ====================

    @GetMapping("/search")
    @Operation(summary = "RAG 关键词检索", description = "根据查询文本检索相关知识文档，返回按相关度排序的结果")
    public R<List<KnowledgeDoc>> search(
            @Parameter(description = "查询关键词") @RequestParam String query,
            @Parameter(description = "返回条数，默认5") @RequestParam(defaultValue = "5") int topK) {
        List<KnowledgeDoc> docs = knowledgeService.search(query, topK);
        return R.ok(docs);
    }

    @GetMapping("/prompt")
    @Operation(summary = "构建 RAG 增强 Prompt", description = "传入用户问题，返回注入知识库上下文后的增强 Prompt 文本")
    public R<String> buildPrompt(
            @Parameter(description = "用户问题") @RequestParam String query) {
        String prompt = knowledgeService.buildRagPrompt(query);
        return R.ok(prompt);
    }

    // ==================== CRUD ====================

    @GetMapping
    @Operation(summary = "获取所有知识文档")
    public R<List<KnowledgeDoc>> listAll() {
        return R.ok(knowledgeService.listAll());
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "按分类获取知识文档")
    public R<List<KnowledgeDoc>> listByCategory(
            @Parameter(description = "分类名称") @PathVariable String category) {
        return R.ok(knowledgeService.listByCategory(category));
    }

    @PostMapping
    @Operation(summary = "新增知识文档")
    public R<KnowledgeDoc> add(
            @Parameter(description = "知识文档 JSON") @RequestBody KnowledgeDoc doc) {
        return R.ok(knowledgeService.addDoc(doc));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识文档")
    public R<KnowledgeDoc> update(
            @Parameter(description = "文档ID") @PathVariable Long id,
            @Parameter(description = "知识文档 JSON") @RequestBody KnowledgeDoc doc) {
        doc.setId(id);
        return R.ok(knowledgeService.updateDoc(doc));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识文档")
    public R<Void> delete(
            @Parameter(description = "文档ID") @PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return R.ok();
    }
}
