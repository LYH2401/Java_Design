package com.campus.tool;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.entity.KnowledgeDoc;
import com.campus.repository.KnowledgeDocMapper;

/**
 * 校园服务工具
 * 提供办事流程和校园卡查询功能，供 Agent 调用
 */
@Component
public class ServiceTool {

    private static final Logger log = LoggerFactory.getLogger(ServiceTool.class);

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final ToolExecutionTracker toolTracker;

    public ServiceTool(KnowledgeDocMapper knowledgeDocMapper,
                       ToolExecutionTracker toolTracker) {
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.toolTracker = toolTracker;
    }

    /**
     * 查询办事流程
     * 从知识库中检索相关的办事流程说明
     *
     * @param keyword 流程关键词，如"选课"、"校园卡补办"、"奖学金申请"、"请假"
     * @return JSON 格式的流程说明
     */
    public String queryProcedure(String keyword) {

        log.info("Tool调用 [queryProcedure]: keyword={}", keyword);
        long startTime = System.currentTimeMillis();
        String params = "{\"keyword\":\"" + escapeJson(keyword) + "\"}";

        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                long duration = System.currentTimeMillis() - startTime;
                String errorResult = "{\"error\": \"请提供要查询的办事流程关键词\"}";
                toolTracker.record("queryProcedure", params, errorResult, duration, false);
                return errorResult;
            }

            String kw = keyword.trim();

            // 从 knowledge_doc 表中搜索相关流程
            List<KnowledgeDoc> docs = knowledgeDocMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDoc>()
                            .and(w -> w.like(KnowledgeDoc::getTitle, kw)
                                    .or()
                                    .like(KnowledgeDoc::getContent, kw))
                            .orderByDesc(KnowledgeDoc::getCreateTime)
                            .last("LIMIT 5")
            );

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"keyword\": \"").append(escapeJson(kw)).append("\", ");
            json.append("\"count\": ").append(docs.size()).append(", ");
            json.append("\"procedures\": [");

            for (int i = 0; i < docs.size(); i++) {
                if (i > 0) {json.append(", ");}
                KnowledgeDoc doc = docs.get(i);
                json.append("{");
                json.append("\"title\": \"").append(escapeJson(doc.getTitle())).append("\", ");
                json.append("\"category\": \"").append(doc.getCategory() != null ? escapeJson(doc.getCategory()) : "").append("\", ");
                json.append("\"content\": \"").append(escapeJson(truncate(doc.getContent(), 500))).append("\"");
                json.append("}");
            }

            json.append("]}");
            String result = json.toString();

            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("queryProcedure", params,
                    result.length() > 500 ? result.substring(0, 500) + "..." : result,
                    duration, true);
            return result;
        } catch (Exception e) {
            log.error("queryProcedure 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"查询办事流程失败: " + e.getMessage() + "\"}";
            toolTracker.record("queryProcedure", params, errorResult, duration, false);
            return errorResult;
        }
    }

    /**
     * 查询校园卡相关信息
     * 包括余额、充值方式、挂失/补办流程等
     *
     * @param action 操作类型：balance(余额)、recharge(充值方式)、lost(挂失)、replace(补办)、info(综合信息)
     * @return JSON 格式的校园卡信息
     */
    public String queryCampusCard(String action) {

        log.info("Tool调用 [queryCampusCard]: action={}", action);
        long startTime = System.currentTimeMillis();
        String params = "{\"action\":\"" + escapeJson(action) + "\"}";

        try {
            String act = (action != null && !action.trim().isEmpty()) ? action.trim() : "info";

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"action\": \"").append(escapeJson(act)).append("\", ");

            switch (act.toLowerCase()) {
                case "balance":
                    json.append("\"balance\": \"123.50\", ");
                    json.append("\"lastRecharge\": \"2026-05-15\", ");
                    json.append("\"cardStatus\": \"正常\", ");
                    json.append("\"note\": \"此为示例余额数据，实际余额以校园卡系统为准\"");
                    break;

                case "recharge":
                    json.append("\"methods\": [");
                    json.append("{\"name\": \"微信充值\", \"steps\": \"关注'智慧校园'公众号 → 校园服务 → 一卡通充值\"},");
                    json.append("{\"name\": \"支付宝充值\", \"steps\": \"搜索'校园一卡通'小程序 → 选择学校 → 输入金额\"},");
                    json.append("{\"name\": \"自助充值机\", \"steps\": \"食堂一楼自助充值机，支持50/100元纸币\"},");
                    json.append("{\"name\": \"人工充值\", \"steps\": \"行政楼一楼财务处窗口，工作日 9:00-16:30\"}");
                    json.append("], ");
                    json.append("\"note\": \"线上充值后需在食堂POS机刷卡生效\"");
                    break;

                case "lost":
                    json.append("\"steps\": [");
                    json.append("{\"step\": 1, \"action\": \"立即挂失\", \"detail\": \"通过'智慧校园'公众号 → 校园服务 → 卡片挂失\"},");
                    json.append("{\"step\": 2, \"action\": \"电话挂失\", \"detail\": \"拨打校园卡服务热线 027-12345678 转 3\"},");
                    json.append("{\"step\": 3, \"action\": \"现场挂失\", \"detail\": \"前往行政楼一楼校园卡服务中心\"}");
                    json.append("], ");
                    json.append("\"note\": \"挂失后卡片立即失效，余额自动冻结保护\"");
                    break;

                case "replace":
                    json.append("\"steps\": [");
                    json.append("{\"step\": 1, \"action\": \"携带证件\", \"detail\": \"携带本人身份证和学生证\"},");
                    json.append("{\"step\": 2, \"action\": \"前往办理\", \"detail\": \"行政楼一楼校园卡服务中心窗口\"},");
                    json.append("{\"step\": 3, \"action\": \"缴纳工本费\", \"detail\": \"补办工本费 20 元（现金/扫码均可）\"},");
                    json.append("{\"step\": 4, \"action\": \"领取新卡\", \"detail\": \"现场制卡，约5-10分钟可取\"}");
                    json.append("], ");
                    json.append("\"cost\": \"20元\", ");
                    json.append("\"processingTime\": \"5-10分钟\", ");
                    json.append("\"location\": \"行政楼一楼校园卡服务中心\"");
                    break;

                case "info":
                default:
                    json.append("\"cardFunctions\": [\"食堂消费\", \"图书馆借阅\", \"门禁通行\", \"水电缴费\", \"打印复印\"], ");
                    json.append("\"rechargeMethods\": \"微信/支付宝/自助机/人工\", ");
                    json.append("\"lostProcedure\": \"公众号挂失 → 证件补办(20元) → 领取新卡\", ");
                    json.append("\"serviceHotline\": \"027-12345678 转 3\", ");
                    json.append("\"serviceLocation\": \"行政楼一楼校园卡服务中心\", ");
                    json.append("\"serviceTime\": \"工作日 9:00-12:00, 14:00-16:30\"");
                    break;
            }

            json.append("}");
            String result = json.toString();

            long duration = System.currentTimeMillis() - startTime;
            toolTracker.record("queryCampusCard", params,
                    result.length() > 500 ? result.substring(0, 500) + "..." : result,
                    duration, true);
            return result;
        } catch (Exception e) {
            log.error("queryCampusCard 执行异常", e);
            long duration = System.currentTimeMillis() - startTime;
            String errorResult = "{\"error\": \"查询校园卡信息失败: " + e.getMessage() + "\"}";
            toolTracker.record("queryCampusCard", params, errorResult, duration, false);
            return errorResult;
        }
    }

    // ==================== 辅助方法 ====================

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
