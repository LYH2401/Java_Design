package com.campus.service;

import com.campus.entity.Conversation;
import com.campus.entity.Message;

import java.util.List;

public interface ConversationService {

    /**
     * 创建新会话（指定模式）
     * @param userId       用户 ID
     * @param firstMessage 首条消息内容（用于生成标题）
     * @param mode         会话模式：NORMAL（保留记录）/ INCOGNITO（无痕）
     */
    Conversation createConversation(Long userId, String firstMessage, String mode);

    /**
     * 创建新会话（默认 NORMAL 模式）
     * @param userId       用户 ID
     * @param firstMessage 首条消息内容（用于生成标题）
     */
    Conversation createConversation(Long userId, String firstMessage);

    /** 获取用户的所有会话 */
    List<Conversation> listConversations(Long userId);

    /** 删除会话 */
    void deleteConversation(Long conversationId);

    /**
     * 加载会话最近 N 轮历史（1轮 = USER + ASSISTANT）
     * @param conversationId 会话 ID
     * @param maxRounds      最大轮次数（默认 10）
     * @return               按时间升序的消息列表
     */
    List<Message> loadRecentRounds(Long conversationId, int maxRounds);

    /**
     * 更新会话标题（自动从首条用户消息截取）
     * @param conversationId 会话 ID
     * @param firstMessage   首条用户消息
     */
    void updateTitle(Long conversationId, String firstMessage);

    /**
     * 清理过期会话（删除 N 天前创建的会话及关联消息）
     * 同时清理所有 INCOGNITO 模式会话
     * @param retentionDays 保留天数
     * @return              清理的会话数量
     */
    int cleanExpiredConversations(int retentionDays);
}
