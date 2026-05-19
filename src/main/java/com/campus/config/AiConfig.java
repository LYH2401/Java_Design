package com.campus.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public ChatClient.Builder chatClientBuilder(DashScopeChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
