package com.campus.service.impl;

import com.campus.entity.Conversation;
import com.campus.repository.ConversationMapper;
import com.campus.repository.MessageMapper;
import com.campus.service.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public ConversationServiceImpl(ConversationMapper conversationMapper, MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public Conversation createConversation(Long userId, String title) {
        Conversation conv = new Conversation();
        conv.setUserId(userId);
        conv.setTitle(title != null ? title : "新对话");
        conv.setCreateTime(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    @Override
    public List<Conversation> listConversations(Long userId) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getCreateTime)
        );
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        // 先删除关联消息
        messageMapper.delete(new LambdaQueryWrapper<com.campus.entity.Message>()
                .eq(com.campus.entity.Message::getConversationId, conversationId));
        // 再删除会话
        conversationMapper.deleteById(conversationId);
    }
}
