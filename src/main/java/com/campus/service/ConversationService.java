package com.campus.service;

import com.campus.entity.Conversation;
import java.util.List;

public interface ConversationService {

    /** 创建新会话 */
    Conversation createConversation(Long userId, String title);

    /** 获取用户的所有会话 */
    List<Conversation> listConversations(Long userId);

    /** 删除会话 */
    void deleteConversation(Long conversationId);
}
