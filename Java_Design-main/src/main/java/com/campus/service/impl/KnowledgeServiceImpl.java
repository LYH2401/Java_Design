package com.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.entity.KnowledgeDoc;
import com.campus.repository.KnowledgeDocMapper;
import com.campus.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 校园知识库服务实现
 * 基于关键词匹配的简易 RAG 检索
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    private final KnowledgeDocMapper knowledgeDocMapper;

    public KnowledgeServiceImpl(KnowledgeDocMapper knowledgeDocMapper) {
        this.knowledgeDocMapper = knowledgeDocMapper;
    }

    /**
     * 关键词检索：对用户 query 分词后，在 title 和 content 中 LIKE 匹配，
     * 按命中次数降序排列，返回 topK 条。
     */
    @Override
    public List<KnowledgeDoc> search(String query, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 加载全部知识文档（知识库规模可控时用全量+内存排序）
        List<KnowledgeDoc> allDocs = knowledgeDocMapper.selectList(null);
        if (allDocs.isEmpty()) {
            log.debug("知识库为空，无法检索");
            return Collections.emptyList();
        }

        // 2. 中文分词：提取连续中文字符 + 连续英文字母作为关键词
        List<String> keywords = tokenize(query);

        // 3. 对每篇文档计算相关性得分（命中关键词次数 + 命中则额外加分）
        List<ScoredDoc> scored = new ArrayList<>();
        for (KnowledgeDoc doc : allDocs) {
            int score = 0;
            String title = doc.getTitle() != null ? doc.getTitle() : "";
            String content = doc.getContent() != null ? doc.getContent() : "";

            for (String kw : keywords) {
                if (title.contains(kw)) {
                    score += 5; // 标题命中权重更高
                }
                if (content.contains(kw)) {
                    score += 2;
                }
            }

            if (score > 0) {
                scored.add(new ScoredDoc(doc, score));
            }
        }

        // 4. 按得分降序，取 topK
        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        List<KnowledgeDoc> result = scored.stream()
                .limit(Math.max(topK, 1))
                .map(s -> s.doc)
                .collect(Collectors.toList());

        log.debug("RAG 检索: query=\"{}\", keywords={}, 命中={}篇, topK={}",
                query, keywords, result.size(), topK);
        return result;
    }

    /**
     * 构建 RAG 增强 Prompt：将检索到的知识上下文注入到用户问题中
     */
    @Override
    public String buildRagPrompt(String userMessage) {
        List<KnowledgeDoc> docs = search(userMessage, 5);

        if (docs.isEmpty()) {
            // 无相关知识 → 返回原始问题
            return userMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下校园知识库内容回答用户问题。如果知识库内容不足以回答，请如实告知。\n\n");
        sb.append("【校园知识库】\n");

        for (int i = 0; i < docs.size(); i++) {
            KnowledgeDoc doc = docs.get(i);
            sb.append("--- 知识片段 ").append(i + 1);
            if (doc.getCategory() != null && !doc.getCategory().isEmpty()) {
                sb.append("（分类：").append(doc.getCategory()).append("）");
            }
            sb.append(" ---\n");
            sb.append("标题：").append(doc.getTitle()).append("\n");
            sb.append("内容：").append(doc.getContent()).append("\n\n");
        }

        sb.append("【用户问题】\n");
        sb.append(userMessage);
        sb.append("\n\n请用中文简洁准确地回答。");

        return sb.toString();
    }

    // ==================== CRUD ====================

    @Override
    public List<KnowledgeDoc> listAll() {
        return knowledgeDocMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDoc>()
                        .orderByDesc(KnowledgeDoc::getCreateTime)
        );
    }

    @Override
    public List<KnowledgeDoc> listByCategory(String category) {
        return knowledgeDocMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getCategory, category)
                        .orderByDesc(KnowledgeDoc::getCreateTime)
        );
    }

    @Override
    public KnowledgeDoc addDoc(KnowledgeDoc doc) {
        if (doc.getCreateTime() == null) {
            doc.setCreateTime(LocalDateTime.now());
        }
        knowledgeDocMapper.insert(doc);
        log.info("新增知识文档: id={}, title={}", doc.getId(), doc.getTitle());
        return doc;
    }

    @Override
    public KnowledgeDoc updateDoc(KnowledgeDoc doc) {
        knowledgeDocMapper.updateById(doc);
        log.info("更新知识文档: id={}, title={}", doc.getId(), doc.getTitle());
        return doc;
    }

    @Override
    public boolean deleteDoc(Long id) {
        int rows = knowledgeDocMapper.deleteById(id);
        log.info("删除知识文档: id={}, 影响行数={}", id, rows);
        return rows > 0;
    }

    // ==================== 内部类 ====================

    /**
     * 评分包装
     */
    private static class ScoredDoc {
        final KnowledgeDoc doc;
        final int score;

        ScoredDoc(KnowledgeDoc doc, int score) {
            this.doc = doc;
            this.score = score;
        }
    }

    // ==================== 分词工具 ====================

    /**
     * 简易中文分词：提取连续的中文字符和连续的英文字母/数字作为关键词，
     * 过滤掉单字和停用词。
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();

        // 提取中文词（连续2个以上汉字）
        java.util.regex.Matcher cn = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,}").matcher(text);
        while (cn.find()) {
            String w = cn.group();
            if (!isStopWord(w)) {
                tokens.add(w);
            }
        }

        // 提取英文/数字词（连续2个以上）
        java.util.regex.Matcher en = java.util.regex.Pattern.compile("[a-zA-Z0-9]{2,}").matcher(text);
        while (en.find()) {
            tokens.add(en.group().toLowerCase());
        }

        return tokens;
    }

    /**
     * 简易停用词判断
     */
    private static boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
                "请问", "可以", "什么", "怎么", "如何", "哪里", "哪个",
                "这个", "那个", "一下", "帮我", "我想", "我要", "需要",
                "有没有", "是不是", "能否", "吗", "呢", "吧", "啊", "哦"
        );
        return stopWords.contains(word);
    }
}
