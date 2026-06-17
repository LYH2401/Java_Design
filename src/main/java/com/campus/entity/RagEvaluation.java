package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("rag_evaluation")
public class RagEvaluation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    @TableField("rag_enabled")
    private Integer ragEnabled;

    @TableField("retrieved_docs")
    private String retrievedDocs; // JSON 字符串

    @TableField("top_similarity")
    private BigDecimal topSimilarity;

    @TableField("response_content")
    private String responseContent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    // ==================== 构造函数 ====================

    public RagEvaluation() {}

    public RagEvaluation(Long id, Long messageId, Integer ragEnabled, String retrievedDocs,
                         BigDecimal topSimilarity, String responseContent, LocalDateTime createTime) {
        this.id = id;
        this.messageId = messageId;
        this.ragEnabled = ragEnabled;
        this.retrievedDocs = retrievedDocs;
        this.topSimilarity = topSimilarity;
        this.responseContent = responseContent;
        this.createTime = createTime;
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public Integer getRagEnabled() { return ragEnabled; }
    public void setRagEnabled(Integer ragEnabled) { this.ragEnabled = ragEnabled; }

    public String getRetrievedDocs() { return retrievedDocs; }
    public void setRetrievedDocs(String retrievedDocs) { this.retrievedDocs = retrievedDocs; }

    public BigDecimal getTopSimilarity() { return topSimilarity; }
    public void setTopSimilarity(BigDecimal topSimilarity) { this.topSimilarity = topSimilarity; }

    public String getResponseContent() { return responseContent; }
    public void setResponseContent(String responseContent) { this.responseContent = responseContent; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
