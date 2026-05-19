package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
