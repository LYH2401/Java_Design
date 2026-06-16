package com.campus.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.dto.R;
import com.campus.dto.ResultCode;
import com.campus.entity.AgentExecutionLog;
import com.campus.repository.AgentExecutionLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent 统计控制器
 * 提供工具调用统计、执行日志分页查询
 */
@RestController
@RequestMapping("/api/agent")
public class AgentStatisticsController {

    private static final Logger log = LoggerFactory.getLogger(AgentStatisticsController.class);

    private final AgentExecutionLogMapper executionLogMapper;

    public AgentStatisticsController(AgentExecutionLogMapper executionLogMapper) {
        this.executionLogMapper = executionLogMapper;
    }

    /**
     * 获取各 Tool 调用统计
     * 返回每个工具的名称、调用次数、平均耗时（ms）
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        // 查询所有 tool_called 不为空的记录
        List<AgentExecutionLog> allLogs = executionLogMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionLog>()
                        .isNotNull(AgentExecutionLog::getToolCalled)
                        .ne(AgentExecutionLog::getToolCalled, "")
        );

        // 按 tool_called 分组统计
        Map<String, ToolStats> statsMap = new LinkedHashMap<>();
        for (AgentExecutionLog logEntry : allLogs) {
            String toolName = logEntry.getToolCalled();
            if (toolName == null || toolName.isEmpty()){ continue;}

            ToolStats stats = statsMap.computeIfAbsent(toolName, ToolStats::new);
            stats.count++;
            if (logEntry.getExecutionTimeMs() != null && logEntry.getExecutionTimeMs() > 0) {
                stats.totalDuration += logEntry.getExecutionTimeMs();
            }
        }

        // 构建返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTools", statsMap.size());

        // 总调用次数
        int totalCalls = statsMap.values().stream().mapToInt(s -> s.count).sum();
        result.put("totalCalls", totalCalls);

        List<Map<String, Object>> toolStatsList = new ArrayList<>();
        for (Map.Entry<String, ToolStats> entry : statsMap.entrySet()) {
            ToolStats s = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("toolName", s.toolName);
            item.put("callCount", s.count);
            item.put("avgDurationMs", s.count > 0 ? Math.round((double) s.totalDuration / s.count) : 0);
            item.put("totalDurationMs", s.totalDuration);
            toolStatsList.add(item);
        }
        result.put("tools", toolStatsList);

        log.info("Agent 统计查询: 工具数={}, 总调用次数={}", statsMap.size(), totalCalls);
        return R.ok(result);
    }

    /**
     * 分页查询 Agent 执行日志
     *
     * @param page     页码（默认1）
     * @param pageSize 每页条数（默认20，最大100）
     * @return 分页日志数据
     */
    @GetMapping("/execution-logs")
    public R<Map<String, Object>> getExecutionLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        // 参数校验
        if (page < 1) {page = 1;}
        if (pageSize < 1) {pageSize = 20;}
        if (pageSize > 100){ pageSize = 100;}

        // MyBatis-Plus 分页查询，按创建时间倒序
        Page<AgentExecutionLog> mpPage = new Page<>(page, pageSize);
        Page<AgentExecutionLog> resultPage = executionLogMapper.selectPage(mpPage,
                new LambdaQueryWrapper<AgentExecutionLog>()
                        .orderByDesc(AgentExecutionLog::getCreateTime)
        );

        // 构建返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", resultPage.getTotal());
        result.put("totalPages", resultPage.getPages());

        // 转换每条日志为 Map（避免序列化问题）
        List<Map<String, Object>> logs = new ArrayList<>();
        for (AgentExecutionLog logEntry : resultPage.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", logEntry.getId());
            item.put("conversationId", logEntry.getConversationId());
            item.put("userMessage", logEntry.getUserMessage());
            item.put("agentIntent", logEntry.getAgentIntent());
            item.put("toolCalled", logEntry.getToolCalled());
            item.put("toolParams", logEntry.getToolParams());
            item.put("toolResult", logEntry.getToolResult());
            item.put("finalResponse", logEntry.getFinalResponse());
            item.put("executionTimeMs", logEntry.getExecutionTimeMs());
            item.put("createTime", logEntry.getCreateTime());
            logs.add(item);
        }
        result.put("records", logs);

        log.info("执行日志分页查询: page={}, pageSize={}, total={}", page, pageSize, resultPage.getTotal());
        return R.ok(result);
    }

    // ==================== 内部类 ====================

    private static class ToolStats {
        String toolName;
        int count;
        long totalDuration;

        ToolStats(String toolName) {
            this.toolName = toolName;
            this.count = 0;
            this.totalDuration = 0;
        }
    }
}
