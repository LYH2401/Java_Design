package com.campus.tool;

import com.campus.dto.RepairOrderCreateDTO;
import com.campus.service.RepairService;
import com.campus.vo.RepairOrderVO;
import com.campus.vo.RepairStatsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 校园报修工具
 * 提供报修提交、进度查询、统计查询功能，供 Agent 调用
 */
@Component
public class RepairTool {

    private static final Logger log = LoggerFactory.getLogger(RepairTool.class);

    /** 默认用户 ID（演示用，后续可从 JWT 提取） */
    private static final Long DEFAULT_USER_ID = 1L;

    private final RepairService repairService;
    private final ToolExecutionTracker toolTracker;

    public RepairTool(RepairService repairService, ToolExecutionTracker toolTracker) {
        this.repairService = repairService;
        this.toolTracker = toolTracker;
    }

    /**
     * 帮学生提交报修单
     *
     * @param title        报修问题标题
     * @param description  报修问题详细描述
     * @param location     报修地点
     * @param urgencyLevel 紧急程度（URGENT/HIGH/NORMAL/LOW）
     * @return JSON 格式的报修结果
     */
    public String submitRepair(String title, String description, String location, String urgencyLevel) {

        log.info("Tool调用 [submitRepair]: title={}, location={}, urgencyLevel={}", title, location, urgencyLevel);
        long startTime = System.currentTimeMillis();
        String params = buildParams("title", title, "description", description,
                "location", location, "urgencyLevel", urgencyLevel);

        try {
            if (title == null || title.trim().isEmpty()) {
                long duration = System.currentTimeMillis() - startTime;
                String errorResult = "{\"error\": \"请提供报修问题标题\"}";
                toolTracker.record("submitRepair", params, errorResult, duration, false);
                return errorResult;
            }

            RepairOrderCreateDTO dto = new RepairOrderCreateDTO();
            dto.setTitle(title.trim());
            dto.setDescription(description != null ? description.trim() : "");
            dto.setLocation(location != null ? location.trim() : "");
            dto.setUrgencyLevel(urgencyLevel != null ? urgencyLevel.trim().toUpperCase() : "NORMAL");
            if (!isValidUrgency(dto.getUrgencyLevel())) {
                dto.setUrgencyLevel("NORMAL");
            }

            RepairOrderVO vo = repairService.createOrder(DEFAULT_USER_ID, dto);

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": true, ");
            json.append("\"orderNo\": \"").append(escapeJson(vo.getOrderNo())).append("\", ");
            json.append("\"orderId\": ").append(vo.getId()).append(", ");
            json.append("\"title\": \"").append(escapeJson(vo.getTitle())).append("\", ");
            json.append("\"status\": \"PENDING\", ");
            json.append("\"message\": \"报修单已成功提交，请耐心等待维修员处理\"");
            json.append("}");
            String result = json.toString();

            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("submitRepair", params, result, duration, true);
            return result;
        } catch (Exception e) {
            log.error("submitRepair 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"提交报修失败: " + e.getMessage() + "\"}";
            toolTracker.record("submitRepair", params, errorResult, duration, false);
            return errorResult;
        }
    }

    /**
     * 查询报修单处理进度
     *
     * @param orderNo 报修单号（如 REP2026061510455284）
     * @return JSON 格式的报修进度信息
     */
    public String queryRepairStatus(String orderNo) {

        log.info("Tool调用 [queryRepairStatus]: orderNo={}", orderNo);
        long startTime = System.currentTimeMillis();
        String params = "{\"orderNo\":\"" + escapeJson(orderNo) + "\"}";

        try {
            if (orderNo == null || orderNo.trim().isEmpty()) {
                long duration = System.currentTimeMillis() - startTime;
                String errorResult = "{\"error\": \"请提供报修单号\"}";
                toolTracker.record("queryRepairStatus", params, errorResult, duration, false);
                return errorResult;
            }

            RepairOrderVO vo = queryByOrderNo(orderNo.trim());
            if (vo == null) {
                long duration = System.currentTimeMillis() - startTime;
                String errorResult = "{\"error\": \"未找到报修单号 " + escapeJson(orderNo.trim()) + " 的记录，请核对单号是否正确\"}";
                toolTracker.record("queryRepairStatus", params, errorResult, duration, false);
                return errorResult;
            }

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"orderNo\": \"").append(escapeJson(vo.getOrderNo())).append("\", ");
            json.append("\"title\": \"").append(escapeJson(vo.getTitle())).append("\", ");
            json.append("\"description\": \"").append(escapeJson(truncate(vo.getDescription(), 200))).append("\", ");
            json.append("\"location\": \"").append(escapeJson(vo.getLocation())).append("\", ");
            json.append("\"urgencyLevel\": \"").append(vo.getUrgencyLevel()).append("\", ");
            json.append("\"status\": \"").append(vo.getStatus()).append("\", ");
            json.append("\"statusText\": \"").append(getStatusText(vo.getStatus())).append("\", ");
            if (vo.getAssignedTime() != null) {
                json.append("\"assignedTime\": \"").append(vo.getAssignedTime().toString()).append("\", ");
            }
            if (vo.getCompletedTime() != null) {
                json.append("\"completedTime\": \"").append(vo.getCompletedTime().toString()).append("\", ");
            }
            if (vo.getMaintainerName() != null) {
                json.append("\"maintainerName\": \"").append(escapeJson(vo.getMaintainerName())).append("\", ");
            }
            if (vo.getReviewRating() != null) {
                json.append("\"reviewRating\": ").append(vo.getReviewRating()).append(", ");
            }
            json.append("\"createdTime\": \"").append(vo.getCreateTime() != null ? vo.getCreateTime().toString() : "").append("\"");
            json.append("}");
            String result = json.toString();

            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("queryRepairStatus", params, result, duration, true);
            return result;
        } catch (Exception e) {
            log.error("queryRepairStatus 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"查询报修进度失败: " + e.getMessage() + "\"}";
            toolTracker.record("queryRepairStatus", params, errorResult, duration, false);
            return errorResult;
        }
    }

    /**
     * 查询报修服务统计数据
     *
     * @return JSON 格式的统计数据
     */
    public String queryRepairStats() {

        log.info("Tool调用 [queryRepairStats]");
        long startTime = System.currentTimeMillis();

        try {
            RepairStatsVO stats = repairService.getStats();

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"totalOrders\": ").append(stats.getTotalOrders()).append(", ");
            json.append("\"pendingCount\": ").append(stats.getPendingCount()).append(", ");
            json.append("\"completedCount\": ").append(stats.getCompletedCount()).append(", ");
            json.append("\"avgRating\": ").append(stats.getAvgRating()).append(", ");
            json.append("\"avgResponseTimeMinutes\": ").append(stats.getAvgResponseTime()).append(", ");
            json.append("\"completionRate\": \"")
                    .append(stats.getTotalOrders() > 0
                            ? Math.round(stats.getCompletedCount() * 100.0 / stats.getTotalOrders()) : 0)
                    .append("%\"");
            json.append("}");
            String result = json.toString();

            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("queryRepairStats", "{}", result, duration, true);
            return result;
        } catch (Exception e) {
            log.error("queryRepairStats 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"查询统计数据失败: " + e.getMessage() + "\"}";
            toolTracker.record("queryRepairStats", "{}", errorResult, duration, false);
            return errorResult;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据报修单号查询（通过 listOrders 遍历匹配）
     */
    private RepairOrderVO queryByOrderNo(String orderNo) {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<RepairOrderVO>(1, 100);
        var result = repairService.listOrders(page, null, null);
        for (RepairOrderVO vo : result.getRecords()) {
            if (orderNo.equals(vo.getOrderNo())) {
                return repairService.getOrderDetail(vo.getId());
            }
        }
        return null;
    }

    private String getStatusText(String status) {
        switch (status != null ? status : "") {
            case "PENDING":    return "等待派单，管理员正在安排维修员";
            case "ASSIGNED":   return "已派单，维修员已收到任务";
            case "REPAIRING":  return "维修中，维修员正在处理";
            case "COMPLETED":  return "已完成，问题已修复";
            case "CANCELLED":  return "已取消";
            default:           return status;
        }
    }

    private boolean isValidUrgency(String level) {
        return "URGENT".equals(level) || "HIGH".equals(level)
                || "NORMAL".equals(level) || "LOW".equals(level);
    }

    private String buildParams(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {sb.append(", ");}
            sb.append("\"").append(keyValues[i]).append("\": \"")
                    .append(escapeJson(keyValues[i + 1])).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {return "";}
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String escapeJson(String s) {
        if (s == null) {return "";}
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
