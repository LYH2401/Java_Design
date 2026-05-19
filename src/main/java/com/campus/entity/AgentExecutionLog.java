package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_execution_log")
public class AgentExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("user_message")
    private String userMessage;

    @TableField("agent_intent")
    private String agentIntent;

    @TableField("tool_called")
    private String toolCalled;

    @TableField("tool_params")
    private String toolParams; // JSON 字符串

    @TableField("tool_result")
    private String toolResult;

    @TableField("final_response")
    private String finalResponse;

    @TableField("execution_time_ms")
    private Integer executionTimeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
