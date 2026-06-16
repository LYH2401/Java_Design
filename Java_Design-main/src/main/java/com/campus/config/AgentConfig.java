package com.campus.config;

import com.campus.tool.CourseTool;
import com.campus.tool.NavigationTool;
import com.campus.tool.RepairTool;
import com.campus.tool.ServiceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 核心配置
 * 使用 Spring AI 1.0.0-M5 的 FunctionCallback API 注册工具，
 * 使 ChatClient 具备 Function Calling 能力。
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    /**
     * Agent 系统 Prompt
     * 清晰描述每个 Function 的适用场景，指导模型正确选择工具
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

            ### 校园报修相关 → 使用 RepairTool
            - submitRepair：当用户描述需要维修的问题时调用（漏水、断电、空调故障、网络故障、桌椅损坏等）
               参数：title（问题标题）、description（详细描述）、location（具体地点）、urgencyLevel（紧急程度：URGENT/HIGH/NORMAL/LOW）
               规则：提交前应追问关键信息（具体地点、问题详情、紧急程度），不要直接用模糊描述提交
            - queryRepairStatus：当用户询问报修进度、维修状态时调用
               参数：orderNo（报修单号）
            - queryRepairStats：当用户询问报修整体情况、服务质量统计时调用
               无参数

            ## 报修相关规则
            - 当用户描述物品损坏、漏水、断电、空调故障、网络故障等问题时，使用 submitRepair 工具帮用户提交报修
            - 在提交报修前，追问关键信息：具体地点、联系方式、紧急程度
            - 当用户询问报修进度时，使用 queryRepairStatus 工具
            - 当你收集完报修所需的全部信息（标题、描述、地点、紧急程度），准备提交前，
              必须先输出以下格式的确认卡片让用户确认：
              ```
              [REPAIR_CONFIRM]
              标题：{title}
              地点：{location}
              描述：{description}
              紧急程度：{urgencyLevel}
              [/REPAIR_CONFIRM]
              ```
              输出确认卡片后，等待用户回复"确认"或"取消"。
              用户回复"确认"后，才调用 submitRepair 工具。
              用户回复"取消"后，告知用户已取消报修。

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
     * Agent 专用 ChatClient Bean（DashScope/百炼）
     */
    @Bean
    public ChatClient agentChatClient(OpenAiChatModel chatModel,
                                       CourseTool courseTool,
                                       NavigationTool navigationTool,
                                       ServiceTool serviceTool,
                                       RepairTool repairTool) {
        ChatClient agentClient = ChatClient.builder(chatModel)
                .defaultSystem(AGENT_SYSTEM_PROMPT)
                .defaultFunctions(buildFunctionCallbacks(courseTool, navigationTool, serviceTool, repairTool))
                .build();
        log.info("Agent ChatClient (DashScope) 初始化完成");
        return agentClient;
    }

    /**
     * Agent 专用 ChatClient Bean（DeepSeek）
     */
    @Bean
    public ChatClient deepseekAgentChatClient(
            @org.springframework.beans.factory.annotation.Qualifier("deepseekChatModel") OpenAiChatModel deepseekChatModel,
            CourseTool courseTool,
            NavigationTool navigationTool,
            ServiceTool serviceTool,
            RepairTool repairTool) {
        ChatClient agentClient = ChatClient.builder(deepseekChatModel)
                .defaultSystem(AGENT_SYSTEM_PROMPT)
                .defaultFunctions(buildFunctionCallbacks(courseTool, navigationTool, serviceTool, repairTool))
                .build();
        log.info("Agent ChatClient (DeepSeek) 初始化完成");
        return agentClient;
    }

    private FunctionCallback[] buildFunctionCallbacks(CourseTool courseTool,
                                                        NavigationTool navigationTool,
                                                        ServiceTool serviceTool,
                                                        RepairTool repairTool) {
        return new FunctionCallback[] {
            FunctionCallback.builder()
                .method("queryCourse", String.class, String.class)
                .name("queryCourse")
                .description("查询指定学生的课表。根据学号查询该学生选修的所有课程信息。可指定星期几来筛选某一天的课程。")
                .targetObject(courseTool)
                .build(),
            FunctionCallback.builder()
                .method("queryClassroom", String.class, String.class)
                .name("queryClassroom")
                .description("查询指定时间段的空闲教室。根据星期几和节次，找出当前没有被课程占用的教室。")
                .targetObject(courseTool)
                .build(),
            FunctionCallback.builder()
                .method("queryLocation", String.class, String.class)
                .name("queryLocation")
                .description("查询校园地点信息。可以根据地点名称关键词或分类查询校园建筑、场所的位置和描述。")
                .targetObject(navigationTool)
                .build(),
            FunctionCallback.builder()
                .method("navigate", String.class, String.class)
                .name("navigate")
                .description("校园路径导航。计算从起点到终点的步行/骑行路线，返回距离、方向和导航指引。")
                .targetObject(navigationTool)
                .build(),
            FunctionCallback.builder()
                .method("queryProcedure", String.class)
                .name("queryProcedure")
                .description("查询校园办事流程。根据关键词搜索办事流程说明文档。")
                .targetObject(serviceTool)
                .build(),
            FunctionCallback.builder()
                .method("queryCampusCard", String.class)
                .name("queryCampusCard")
                .description("查询校园卡相关信息。包括余额查询、充值方式、挂失流程、补办流程等。")
                .targetObject(serviceTool)
                .build(),
            FunctionCallback.builder()
                .method("submitRepair", String.class, String.class, String.class, String.class)
                .name("submitRepair")
                .description("帮学生提交校园报修单。当学生描述物品损坏、漏水、断电、空调故障、网络故障等问题时调用。参数：title(问题标题)、description(详细描述)、location(具体地点)、urgencyLevel(紧急程度：URGENT/HIGH/NORMAL/LOW)。")
                .targetObject(repairTool)
                .build(),
            FunctionCallback.builder()
                .method("queryRepairStatus", String.class)
                .name("queryRepairStatus")
                .description("查询报修单处理进度。根据报修单号查询当前状态、维修员信息和预计完成时间。")
                .targetObject(repairTool)
                .build(),
            FunctionCallback.builder()
                .method("queryRepairStats")
                .name("queryRepairStats")
                .description("查询校园报修服务整体统计数据。返回总报修数、待处理数、已完成数、平均评分和平均响应时间。")
                .targetObject(repairTool)
                .build()
        };
    }
}
