package com.campus.service.impl;

import com.campus.entity.AgentExecutionLog;
import com.campus.entity.Message;
import com.campus.repository.AgentExecutionLogMapper;
import com.campus.repository.MessageMapper;
import com.campus.service.AgentService;
import com.campus.service.HitlService;
import com.campus.service.RagService;
import com.campus.tool.ToolExecutionTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 对话服务实现
 * 协调 ChatClient（注册了 @Tool 的 Bean）进行自动工具选择与调用
 * 集成 HitlService（人机协同确认）和 ToolExecutionTracker（工具调用追踪）
 */
@Service
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final ChatClient agentChatClient;
    private final MessageMapper messageMapper;
    private final AgentExecutionLogMapper executionLogMapper;
    private final RagService ragService;
    private final HitlService hitlService;
    private final ToolExecutionTracker toolTracker;

    public AgentServiceImpl(
            @Qualifier("agentChatClient") ChatClient agentChatClient,
            MessageMapper messageMapper,
            AgentExecutionLogMapper executionLogMapper,
            RagService ragService,
            HitlService hitlService,
            ToolExecutionTracker toolTracker) {
        this.agentChatClient = agentChatClient;
        this.messageMapper = messageMapper;
        this.executionLogMapper = executionLogMapper;
        this.ragService = ragService;
        this.hitlService = hitlService;
        this.toolTracker = toolTracker;
    }

    @Override
    public Flux<String> agentChat(Long conversationId, String userMessage) {
        // 检查是否有待确认的操作需要处理
        String confirmResult = hitlService.tryResolveConfirmation(conversationId, userMessage);
        if (confirmResult != null) {
            if (confirmResult.startsWith("CONFIRMED:")) {
                String action = confirmResult.substring("CONFIRMED:".length());
                saveMessage(conversationId, "USER", userMessage);
                String reply = "✅ 操作已确认：「" + action + "」，正在为您执行...";
                saveMessage(conversationId, "ASSISTANT", reply);
                return Flux.just(reply);
            } else if (confirmResult.startsWith("CANCELLED:")) {
                String action = confirmResult.substring("CANCELLED:".length());
                saveMessage(conversationId, "USER", userMessage);
                String reply = "❌ 操作已取消：「" + action + "」。如有需要，请重新发起。";
                saveMessage(conversationId, "ASSISTANT", reply);
                return Flux.just(reply);
            } else if (confirmResult.startsWith("PENDING_RETRY:")) {
                String action = confirmResult.substring("PENDING_RETRY:".length());
                String reply = "⚠️ 请明确回复「是」或「否」来确认以下操作：\n" + action;
                return Flux.just(reply);
            }
        }

        // 1. 保存用户消息
        saveMessage(conversationId, "USER", userMessage);

        // 2. 清除上轮工具追踪记录
        toolTracker.clear();

        long startTime = System.currentTimeMillis();
        StringBuilder fullResponse = new StringBuilder();

        // 3. 调用带 Tool 能力的 ChatClient（流式），工具自动选择与调用
        return agentChatClient.prompt()
                .user(userMessage)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    String response = fullResponse.toString();
                    // 4. 保存 AI 回复
                    saveMessage(conversationId, "ASSISTANT", response);
                    // 5. 记录 Agent 执行日志（包含工具调用信息）
                    int elapsed = (int) (System.currentTimeMillis() - startTime);
                    saveExecutionLog(conversationId, userMessage, response, elapsed);

                    // 6. 检测是否需要用户确认（Hitl）
                    if (hitlService.requiresConfirmation(response)) {
                        String actionDesc = hitlService.extractActionDescription(response);
                        if (actionDesc != null) {
                            hitlService.registerPending(conversationId, actionDesc);
                            log.info("检测到需要确认的操作: conversationId={}, action={}", conversationId, actionDesc);
                        }
                    }
                    // 7. 清除本轮工具追踪
                    toolTracker.clear();
                    log.info("Agent 对话完成: conversationId={}, 耗时={}ms, 工具调用次数={}",
                            conversationId, elapsed, toolTracker.getCallCount());
                })
                .doOnError(e -> {
                    log.error("Agent 调用异常: conversationId={}", conversationId, e);
                    toolTracker.clear();
                });
    }

    /**
     * Agent 对话 + RAG 知识增强
     * 先检索知识库，将检索到的文档作为上下文注入 Prompt，再由 Agent 决定是否调用工具
     */
    public Flux<String> agentChatWithRag(Long conversationId, String userMessage) {
        // 检查是否有待确认的操作需要处理
        String confirmResult = hitlService.tryResolveConfirmation(conversationId, userMessage);
        if (confirmResult != null) {
            if (confirmResult.startsWith("CONFIRMED:")) {
                String action = confirmResult.substring("CONFIRMED:".length());
                saveMessage(conversationId, "USER", userMessage);
                String reply = "✅ 操作已确认：「" + action + "」，正在为您执行...";
                saveMessage(conversationId, "ASSISTANT", reply);
                return Flux.just(reply);
            } else if (confirmResult.startsWith("CANCELLED:")) {
                String action = confirmResult.substring("CANCELLED:".length());
                saveMessage(conversationId, "USER", userMessage);
                String reply = "❌ 操作已取消：「" + action + "」。如有需要，请重新发起。";
                saveMessage(conversationId, "ASSISTANT", reply);
                return Flux.just(reply);
            } else if (confirmResult.startsWith("PENDING_RETRY:")) {
                String action = confirmResult.substring("PENDING_RETRY:".length());
                String reply = "⚠️ 请明确回复「是」或「否」来确认以下操作：\n" + action;
                return Flux.just(reply);
            }
        }

        // 1. 保存用户消息
        saveMessage(conversationId, "USER", userMessage);

        // 2. 清除上轮工具追踪记录
        toolTracker.clear();

        long startTime = System.currentTimeMillis();
        StringBuilder fullResponse = new StringBuilder();

        // 3. RAG 检索相关文档
        List<org.springframework.ai.document.Document> ragDocs = ragService.search(userMessage, 3);
        String ragContext = buildRagContext(ragDocs);

        // 4. 拼接增强 Prompt
        String enhancedPrompt = buildRagEnhancedPrompt(ragContext, userMessage);

        // 5. 调用 Agent ChatClient（带 Tool + RAG 上下文）
        return agentChatClient.prompt()
                .user(enhancedPrompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    String response = fullResponse.toString();
                    saveMessage(conversationId, "ASSISTANT", response);
                    int elapsed = (int) (System.currentTimeMillis() - startTime);
                    saveExecutionLog(conversationId, "[RAG+Agent] " + userMessage, response, elapsed);

                    // 检测是否需要用户确认
                    if (hitlService.requiresConfirmation(response)) {
                        String actionDesc = hitlService.extractActionDescription(response);
                        if (actionDesc != null) {
                            hitlService.registerPending(conversationId, actionDesc);
                            log.info("检测到需要确认的操作(RAG): conversationId={}, action={}", conversationId, actionDesc);
                        }
                    }
                    toolTracker.clear();
                    log.info("RAG+Agent 对话完成: conversationId={}, 耗时={}ms, 检索文档数={}, 工具调用次数={}",
                            conversationId, elapsed, ragDocs.size(), toolTracker.getCallCount());
                })
                .doOnError(e -> {
                    log.error("RAG+Agent 调用异常: conversationId={}", conversationId, e);
                    toolTracker.clear();
                });
    }

    @Override
    public Map<String, Object> getTools() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", 6);

        List<Map<String, Object>> tools = List.of(
                toolInfo("queryCourse", "CourseTool",
                        "查询指定学生的课表。根据学号查询该学生选修的所有课程信息。可指定星期几来筛选某一天的课程。",
                        List.of("studentId(学号)", "date(可选，星期几)")),
                toolInfo("queryClassroom", "CourseTool",
                        "查询指定时间段的空闲教室。根据星期几和节次，找出当前没有被课程占用的教室。",
                        List.of("date(星期几)", "timeSlot(节次如1-2)")),
                toolInfo("queryLocation", "NavigationTool",
                        "查询校园地点信息。根据名称或分类搜索校园内的建筑/场所位置信息。",
                        List.of("name(地点名称)", "category(可选，分类)")),
                toolInfo("navigate", "NavigationTool",
                        "校园路径导航。计算从起点到终点的距离、方向和预计时间。",
                        List.of("from(起点)", "to(终点)")),
                toolInfo("queryProcedure", "ServiceTool",
                        "查询校园办事流程。根据关键词搜索相关办事流程的详细步骤。",
                        List.of("keyword(流程关键词)")),
                toolInfo("queryCampusCard", "ServiceTool",
                        "查询校园卡相关信息。支持余额查询、充值方式、挂失、补办等操作。",
                        List.of("action(balance/recharge/lost/replace/info)"))
        );
        result.put("tools", tools);
        return result;
    }

    // ==================== 私有辅助方法 ====================

    private Map<String, Object> toolInfo(String name, String className,
                                          String description, List<String> params) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", name);
        info.put("className", className);
        info.put("description", description);
        info.put("params", params);
        return info;
    }

    private void saveMessage(Long conversationId, String role, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }

    /**
     * 保存 Agent 执行日志
     * 从 ToolExecutionTracker 中获取本轮的每个工具调用记录，
     * 一个对话轮次可能产生多条日志（每个工具调用一条 + 最终响应一条）
     */
    private void saveExecutionLog(Long conversationId, String userMessage,
                                   String finalResponse, int elapsed) {
        List<ToolExecutionTracker.ToolCallRecord> toolRecords = toolTracker.getRecords();

        if (toolRecords.isEmpty()) {
            // 没有工具调用：记录一条汇总日志
            AgentExecutionLog logEntry = new AgentExecutionLog();
            logEntry.setConversationId(conversationId);
            logEntry.setUserMessage(userMessage);
            logEntry.setAgentIntent("AUTO");
            logEntry.setToolCalled(null);
            logEntry.setToolParams(null);
            logEntry.setToolResult(null);
            logEntry.setFinalResponse(finalResponse);
            logEntry.setExecutionTimeMs(elapsed);
            logEntry.setCreateTime(LocalDateTime.now());
            executionLogMapper.insert(logEntry);
        } else {
            // 有工具调用：每个工具调用产生一条日志记录
            // 最后一条记录同时保存最终响应
            for (int i = 0; i < toolRecords.size(); i++) {
                ToolExecutionTracker.ToolCallRecord record = toolRecords.get(i);

                AgentExecutionLog logEntry = new AgentExecutionLog();
                logEntry.setConversationId(conversationId);
                logEntry.setUserMessage(userMessage);
                logEntry.setAgentIntent("AUTO");
                logEntry.setToolCalled(record.getToolName());
                logEntry.setToolParams(record.getParams());
                logEntry.setToolResult(record.getResult());
                // 最后一条工具记录附带最终响应
                if (i == toolRecords.size() - 1) {
                    logEntry.setFinalResponse(finalResponse);
                } else {
                    logEntry.setFinalResponse(null);
                }
                logEntry.setExecutionTimeMs((int) record.getDurationMs());
                logEntry.setCreateTime(LocalDateTime.now());
                executionLogMapper.insert(logEntry);
            }
        }
    }

    private String buildRagContext(List<org.springframework.ai.document.Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append("【参考资料").append(i + 1).append("】\n");
            sb.append(docs.get(i).getText());
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private String buildRagEnhancedPrompt(String ragContext, String userMessage) {
        if (ragContext == null || ragContext.isEmpty()) {
            return userMessage;
        }
        return """
                ## 参考资料
                %s
                
                ## 用户问题
                %s
                
                请根据上述参考资料回答问题。如果参考资料中包含相关信息，请优先使用；否则使用你自己的知识。
                如果需要调用工具获取更具体的信息（如课表、导航、办事流程），请选择合适的工具。
                """.formatted(ragContext, userMessage);
    }
}
