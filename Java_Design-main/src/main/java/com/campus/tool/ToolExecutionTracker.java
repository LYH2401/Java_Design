package com.campus.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工具执行追踪器（ThreadLocal 隔离）
 * 每个 Agent 对话轮次中，工具调用记录通过此组件收集，
 * 由 AgentServiceImpl 在流式输出完成后统一取出并持久化。
 */
@Component
public class ToolExecutionTracker {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionTracker.class);

    private final ThreadLocal<List<ToolCallRecord>> recordsHolder = ThreadLocal.withInitial(ArrayList::new);

    /**
     * 记录一次工具调用（由各 @Tool 方法内部调用）
     */
    public void record(String toolName, String params, String result, long durationMs, boolean success) {
        ToolCallRecord record = new ToolCallRecord(toolName, params, result, durationMs, success);
        recordsHolder.get().add(record);
        log.debug("工具调用已追踪: tool={}, duration={}ms, success={}", toolName, durationMs, success);
    }

    /**
     * 获取本轮所有工具调用记录（只读）
     */
    public List<ToolCallRecord> getRecords() {
        return Collections.unmodifiableList(recordsHolder.get());
    }

    /**
     * 获取本轮调用次数
     */
    public int getCallCount() {
        return recordsHolder.get().size();
    }

    /**
     * 清除本轮记录（必须在 doOnComplete/doOnError 后调用）
     */
    public void clear() {
        recordsHolder.get().clear();
    }

    /**
     * 工具调用记录
     */
    public static class ToolCallRecord {
        private final String toolName;
        private final String params;
        private final String result;
        private final long durationMs;
        private final boolean success;
        private final LocalDateTime timestamp;

        public ToolCallRecord(String toolName, String params, String result, long durationMs, boolean success) {
            this.toolName = toolName;
            this.params = params;
            this.result = result;
            this.durationMs = durationMs;
            this.success = success;
            this.timestamp = LocalDateTime.now();
        }

        public String getToolName() { return toolName; }
        public String getParams() { return params; }
        public String getResult() { return result; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return success; }
        public LocalDateTime getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("ToolCallRecord{tool='%s', duration=%dms, success=%s}",
                    toolName, durationMs, success);
        }
    }
}
