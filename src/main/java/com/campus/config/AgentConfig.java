package com.campus.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.campus.tool.CourseTool;
import com.campus.tool.NavigationTool;
import com.campus.tool.ServiceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 核心配置
 * 将 @Tool 标注的组件注册到 ChatClient，使其具备 Function Calling 能力
 * <p>
 * 注意：Spring AI Alibaba 1.1.2 中通过 ChatClient.Builder.defaultTools()
 * 直接注册工具对象，无需 ToolRegistry（该 API 在更高版本才引入）
 * </p>
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    /**
     * Agent 系统 Prompt
     * 清晰描述每个 Tool 的适用场景，指导模型正确选择工具
     */
    public static final String AGENT_SYSTEM_PROMPT = """
            你是"校园智能服务小助手"，一所现代化大学的AI助手。

            ## 可用工具及适用场景

            你拥有以下工具可以调用，请根据用户问题的类型选择最合适的工具：

            ### 课程与课表相关 → 使用 CourseTool
            - queryCourse：当用户询问「我的课表」「今天有什么课」「查询课程」时调用
              参数：studentId（学号）、date（可选，星期几）
            - queryClassroom：当用户询问「有没有空教室」「哪里可以自习」时调用
              参数：date（星期几）、timeSlot（节次如1-2）

            ### 校园地点与导航相关 → 使用 NavigationTool
            - queryLocation：当用户询问「XX在哪里」「图书馆在哪儿」「食堂位置」时调用
              参数：name（地点名称）、category（分类，可选）
            - navigate：当用户询问「从A到B怎么走」「如何从图书馆去行政楼」时调用
              参数：from（起点）、to（终点）

            ### 校园服务与办事流程相关 → 使用 ServiceTool
            - queryProcedure：当用户询问办事流程时调用，如「怎么选课」「校园卡怎么补办」「奖学金怎么申请」
              参数：keyword（流程关键词）
            - queryCampusCard：当用户询问校园卡相关问题时调用，如「校园卡余额」「怎么充值」「怎么挂失」「补办校园卡」
              参数：action（balance/recharge/lost/replace/info）

            ## 规则
            1. 优先判断是否需要调用工具，需要则立即调用
            2. 课程/选课/空教室 → CourseTool
            3. 地点/建筑/导航/怎么走 → NavigationTool
            4. 办事流程/校园卡/奖学金/请假/图书馆规则 → ServiceTool
            5. 无法匹配任何工具或工具返回为空 → 用你的知识友好回答
            6. 工具返回 JSON 数据后，请将其转化为自然的中文回答，不要直接输出 JSON
            7. 回答简洁、准确、友好，使用中文
            """;

    /**
     * Agent 专用 ChatClient Bean
     * 通过 ChatClient.Builder.defaultTools() 直接注册带有 @Tool 注解的 Bean，
     * 使 DashScope 模型具备 Function Calling 能力。
     * <p>
     * 独立创建 Builder 以避免与 AiConfig 中的基础 ChatClient Builder 互相干扰。
     * </p>
     */
    @Bean
    public ChatClient agentChatClient(DashScopeChatModel chatModel,
                                       CourseTool courseTool,
                                       NavigationTool navigationTool,
                                       ServiceTool serviceTool) {
        ChatClient agentClient = ChatClient.builder(chatModel)
                .defaultSystem(AGENT_SYSTEM_PROMPT)
                .defaultTools(courseTool, navigationTool, serviceTool)
                .build();
        log.info("Agent ChatClient 初始化完成: 已注册 CourseTool, NavigationTool, ServiceTool");
        return agentClient;
    }
}
