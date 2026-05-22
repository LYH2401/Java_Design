package com.campus.dto;

public class ChatRequest {

    private Long conversationId;
    private String message;
    /** 模型选择：dashscope（默认）、deepseek */
    private String model;

    public ChatRequest() {}

    public ChatRequest(Long conversationId, String message) {
        this.conversationId = conversationId;
        this.message = message;
    }

    public ChatRequest(Long conversationId, String message, String model) {
        this.conversationId = conversationId;
        this.message = message;
        this.model = model;
    }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
