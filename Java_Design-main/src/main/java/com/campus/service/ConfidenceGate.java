package com.campus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * RAG 置信度门控
 * 基于向量相似度阈值判断检索结果是否足够可靠
 * 若最高相似度低于阈值 → 拒绝基于知识库回答，交由兜底逻辑
 */
@Component
public class ConfidenceGate {

    private static final Logger log = LoggerFactory.getLogger(ConfidenceGate.class);

    /** 默认相似度阈值：低于此值认为检索结果不可信 */
    public static final double DEFAULT_THRESHOLD = 0.70;

    private final double threshold;

    public ConfidenceGate() {
        this.threshold = DEFAULT_THRESHOLD;
    }

    /**
     * 检查检索结果是否通过置信度门槛
     *
     * @param docs 向量检索返回的文档列表
     * @return true 表示置信度达标，可基于知识库回答
     */
    public boolean isConfident(List<Document> docs) {
        return isConfident(docs, threshold);
    }

    /**
     * 检查检索结果是否通过指定阈值
     *
     * @param docs      向量检索返回的文档列表
     * @param threshold 自定义阈值 [0.0, 1.0]
     * @return true 表示置信度达标
     */
    public boolean isConfident(List<Document> docs, double threshold) {
        if (docs == null || docs.isEmpty()) {
            log.info("置信度门控: 检索结果为空 → 拒绝回答");
            return false;
        }

        double topSimilarity = extractTopSimilarity(docs);
        boolean passed = topSimilarity >= threshold;

        if (!passed) {
            log.info("置信度门控: 最高相似度 {} 低于阈值 {} → 拒绝基于知识库回答",
                    String.format("%.4f", topSimilarity), threshold);
        } else {
            log.debug("置信度门控: 最高相似度 {} >= 阈值 {} → 通过",
                    String.format("%.4f", topSimilarity), threshold);
        }

        return passed;
    }

    /**
     * 从检索结果中提取最高相似度
     * SimpleVectorStore 将距离/相似度存储在 Document.metadata 中
     * 不同实现可能使用 "similarity"、"score"、"distance" 等键名
     */
    public double extractTopSimilarity(List<Document> docs) {
        double max = 0.0;
        for (Document doc : docs) {
            double sim = extractSimilarity(doc);
            if (sim > max) {
                max = sim;
            }
        }
        return max;
    }

    /**
     * 从单个 Document 中提取相似度
     * 兼容多种 metadata key 命名
     */
    private double extractSimilarity(Document doc) {
        if (doc.getMetadata() == null) {
            return 0.0;
        }

        // 按优先级尝试多种 key
        String[] keys = {"similarity", "score", "distance", "cosine_similarity"};
        for (String key : keys) {
            Object val = doc.getMetadata().get(key);
            if (val != null) {
                try {
                    double sim = Double.parseDouble(val.toString());
                    // distance 是距离度量，值越小越相似，需要转换
                    if ("distance".equals(key)) {
                        // 欧几里得距离: similarity ≈ 1 / (1 + distance)
                        sim = 1.0 / (1.0 + sim);
                    }
                    return sim;
                } catch (NumberFormatException ignored) {
                    // 非数字值，跳过
                }
            }
        }

        return 0.0;
    }

    /**
     * 将相似度转为保留4位小数的 BigDecimal（供 RAG 评估记录使用）
     */
    public BigDecimal toBigDecimal(double similarity) {
        return BigDecimal.valueOf(similarity).setScale(4, RoundingMode.HALF_UP);
    }

    public double getThreshold() {
        return threshold;
    }
}
