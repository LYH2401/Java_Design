package com.campus.service.impl;

import com.campus.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档加载与分块服务实现
 * 从 resources/knowledge/ 加载 .txt 文档并使用 TokenTextSplitter 分块
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    /** 知识文档目录 */
    private static final String KNOWLEDGE_PATH = "classpath:knowledge/*.txt";

    /** 默认分块大小（token 数） */
    private static final int DEFAULT_CHUNK_SIZE = 500;
    /** 默认块间重叠（token 数） */
    private static final int DEFAULT_OVERLAP = 50;

    @Override
    public List<Document> loadDocuments() {
        List<Document> docs = new ArrayList<>();

        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(KNOWLEDGE_PATH);

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".txt")) {
                    continue;
                }

                String content = readResource(resource);
                if (content.isEmpty()) {
                    log.warn("知识文档为空，跳过: {}", filename);
                    continue;
                }

                // 提取文档标题（文件名去掉 .txt 后缀）
                String title = filename.replace(".txt", "");

                Document doc = new Document(content);
                doc.getMetadata().put("title", title);
                doc.getMetadata().put("source", filename);
                doc.getMetadata().put("category", extractCategory(filename));

                docs.add(doc);
                log.info("加载知识文档: {} ({} 字符)", title, content.length());
            }

            log.info("知识文档加载完成，共 {} 篇", docs.size());

        } catch (Exception e) {
            log.error("加载知识文档失败", e);
        }

        return docs;
    }

    @Override
    public List<Document> splitDocuments(List<Document> documents, int chunkSize, int overlap) {
        int cs = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        int ol = overlap >= 0 ? overlap : DEFAULT_OVERLAP;

        TokenTextSplitter splitter = new TokenTextSplitter(cs, ol, 5, 1000, true);

        List<Document> chunks = new ArrayList<>();
        for (Document doc : documents) {
            List<Document> docChunks = splitter.apply(List.of(doc));
            // 继承元数据
            for (Document chunk : docChunks) {
                chunk.getMetadata().putAll(doc.getMetadata());
            }
            chunks.addAll(docChunks);
        }

        log.info("文档分块完成: 原始{}篇 → {}块 (chunkSize={}, overlap={})",
                documents.size(), chunks.size(), cs, ol);
        return chunks;
    }

    // ==================== 私有方法 ====================

    /**
     * 读取 Resource 为字符串
     */
    private String readResource(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("读取文件失败: {}", resource.getFilename(), e);
            return "";
        }
    }

    /**
     * 从文件名推断分类（示例映射）
     */
    private String extractCategory(String filename) {
        if (filename.contains("图书馆")) return "图书馆";
        if (filename.contains("奖学金")) return "奖学金";
        if (filename.contains("校园卡")) return "校园卡";
        if (filename.contains("选课") || filename.contains("学分")) return "选课与学分";
        if (filename.contains("交通") || filename.contains("导航")) return "校园交通";
        return "其他";
    }
}
