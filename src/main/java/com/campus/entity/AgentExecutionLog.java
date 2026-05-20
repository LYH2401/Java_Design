package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

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
    private String toolParams;

    @TableField("tool_result")
    private String toolResult;

    @TableField("final_response")
    private String finalResponse;

    @TableField("execution_time_ms")
    private Integer executionTimeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    public AgentExecutionLog() {}

    public AgentExecutionLog(Long id, Long conversationId, String userMessage, String agentIntent,
                             String toolCalled, String toolParams, String toolResult,
                             String finalResponse, Integer executionTimeMs, LocalDateTime createTime) {
        this.id = id;
        this.conversationId = conversationId;
        this.userMessage = userMessage;
        this.agentIntent = agentIntent;
        this.toolCalled = toolCalled;
        this.toolParams = toolParams;
        this.toolResult = toolResult;
        this.finalResponse = finalResponse;
        this.executionTimeMs = executionTimeMs;
        this.createTime = createTime;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public String getAgentIntent() { return agentIntent; }
    public void setAgentIntent(String agentIntent) { this.agentIntent = agentIntent; }

    public String getToolCalled() { return toolCalled; }
    public void setToolCalled(String toolCalled) { this.toolCalled = toolCalled; }

    public String getToolParams() { return toolParams; }
    public void setToolParams(String toolParams) { this.toolParams = toolParams; }

    public String getToolResult() { return toolResult; }
    public void setToolResult(String toolResult) { this.toolResult = toolResult; }

    public String getFinalResponse() { return finalResponse; }
    public void setFinalResponse(String finalResponse) { this.finalResponse = finalResponse; }

    public Integer getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Integer executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
