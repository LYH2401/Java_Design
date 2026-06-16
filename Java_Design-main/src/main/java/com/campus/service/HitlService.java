package com.campus.service;

/**
 * Human-in-the-Loop 人机协同服务
 * 管理敏感操作的确认流程：Agent 标记 "[需要确认]" → 用户回复 "是/否"
 */
public interface HitlService {

    /**
     * 检测 Agent 回复是否包含确认请求标记
     *
     * @param response Agent 回复内容
     * @return true 表示需要用户确认
     */
    boolean requiresConfirmation(String response);

    /**
     * 从 "[需要确认]" 标记中提取操作描述
     *
     * @param response 包含 "[需要确认]" 的 Agent 回复
     * @return 操作描述文本
     */
    String extractActionDescription(String response);

    /**
     * 注册一个待确认的敏感操作
     *
     * @param conversationId    会话 ID
     * @param actionDescription 操作描述（从 Agent 回复中提取）
     */
    void registerPending(Long conversationId, String actionDescription);

    /**
     * 处理用户对确认请求的回复
     *
     * @param conversationId 会话 ID
     * @param userReply      用户回复（是/否/yes/no/确认/取消）
     * @return "CONFIRMED:" 或 "CANCELLED:" 前缀的结果，null 表示无待确认操作
     */
    String processConfirmation(Long conversationId, String userReply);

    /**
     * 检查指定会话是否有待确认的操作
     *
     * @param conversationId 会话 ID
     * @return true 表示有待确认操作
     */
    boolean hasPendingConfirmation(Long conversationId);

    /**
     * 如果上一轮 Agent 回复需要确认、且本轮用户回复是/否，返回重组后的确认消息
     * 否则返回 null，表示走正常 Agent 流程
     *
     * @param conversationId 会话 ID
     * @param userMessage    用户本轮输入
     * @return 确认后的重组消息，或 null
     */
    String tryResolveConfirmation(Long conversationId, String userMessage);
}
