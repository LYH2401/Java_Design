package com.campus.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 核心配置
 * - 主模型：阿里云百炼 DashScope（OpenAI 兼容模式），默认 qwen-turbo
 * - 辅助模型：DeepSeek（OpenAI 兼容模式），默认 deepseek-v4-flash
 */
@Configuration
public class AiConfig {

    /**
     * 系统角色设定：校园智能服务小助手
     */
    private static final String SYSTEM_PROMPT = """
            你是"校园智能服务小助手"，一所现代化大学的AI助手。你的职责包括：
            1. 回答校园相关的问题（图书馆、食堂、课程、建筑导航等）
            2. 提供办事流程咨询（选课、补办校园卡、请假等）
            3. 帮助用户查询课表信息
            4. 引导用户找到正确的校园服务入口

            要求：
            - 回答简洁、准确、友好
            - 如果问题超出你的知识范围，诚实地告知并建议联系相关部门
            - 使用中文回答
            - 回答中涉及地点时，给出清晰的方位指引
            """;

    // ---- 默认 ChatClient（DashScope / 百炼） ----
    // 注：ChatClient.Builder 由 Spring AI 自动配置提供（ChatClientAutoConfiguration），
    // 这里不再自行创建，仅通过注入自动配置的 Builder 来定制默认 System Prompt。

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    // ---- DeepSeek ChatClient ----

    @Bean
    public OpenAiApi deepseekOpenAiApi(
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url}") String baseUrl) {
        return new OpenAiApi(baseUrl, apiKey);
    }

    @Bean
    public OpenAiChatModel deepseekChatModel(OpenAiApi deepseekOpenAiApi,
                                              @Value("${deepseek.chat.options.model:deepseek-v4-flash}") String model,
                                              @Value("${deepseek.chat.options.temperature:0.7}") Double temperature) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();
        return new OpenAiChatModel(deepseekOpenAiApi, options);
    }

    @Bean
    public ChatClient deepseekChatClient(OpenAiChatModel deepseekChatModel) {
        return ChatClient.builder(deepseekChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
