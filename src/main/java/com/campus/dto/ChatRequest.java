package com.campus.dto;

public class ChatRequest {

    private Long conversationId;
    private String message;
    /** 模型选择：dashscope（默认）、deepseek，或用户自定义 */
    private String model;
    /** 用户自定义 API Key（优先于服务端配置） */
    private String apiKey;
    /** 用户自定义 API Base URL（优先于服务端配置） */
    private String baseUrl;

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

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean hasCustomApiConfig() {
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }
}
