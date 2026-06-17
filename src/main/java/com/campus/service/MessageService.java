package com.campus.service;

import com.campus.entity.Message;

import java.util.List;

/**
 * 消息管理服务接口
 */
public interface MessageService {

    /**
     * 分页查询会话历史消息
     * @param conversationId 会话 ID
     * @param page           页码（从 1 开始）
     * @param size           每页大小
     * @return               按时间升序的消息列表
     */
    List<Message> listByConversation(Long conversationId, int page, int size);

    /**
     * 查询会话消息总数
     */
    long countByConversation(Long conversationId);

    /**
     * 按会话 ID 删除所有消息
     */
    int deleteByConversation(Long conversationId);
}
