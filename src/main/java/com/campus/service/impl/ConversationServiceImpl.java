package com.campus.service.impl;

import com.campus.entity.Conversation;
import com.campus.entity.Message;
import com.campus.repository.ConversationMapper;
import com.campus.repository.MessageMapper;
import com.campus.service.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    /** 默认历史轮次 */
    private static final int DEFAULT_MAX_ROUNDS = 10;
    /** 标题最大字数 */
    private static final int TITLE_MAX_LENGTH = 20;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public ConversationServiceImpl(ConversationMapper conversationMapper, MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public Conversation createConversation(Long userId, String firstMessage) {
        String title = extractTitle(firstMessage);

        Conversation conv = new Conversation();
        conv.setUserId(userId);
        conv.setTitle(title);
        conv.setCreateTime(LocalDateTime.now());
        conversationMapper.insert(conv);

        log.info("创建会话: id={}, userId={}, title={}", conv.getId(), userId, title);
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
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId));
        // 再删除会话
        conversationMapper.deleteById(conversationId);
        log.info("删除会话及关联消息: conversationId={}", conversationId);
    }

    @Override
    public List<Message> loadRecentRounds(Long conversationId, int maxRounds) {
        int rounds = maxRounds > 0 ? maxRounds : DEFAULT_MAX_ROUNDS;
        int messageLimit = rounds * 2; // 每轮 = USER + ASSISTANT

        List<Message> allMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByDesc(Message::getCreateTime)
                        .last("LIMIT " + messageLimit)
        );

        // 查询结果按时间降序，需逆序返回
        List<Message> ordered = new ArrayList<>();
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            ordered.add(allMessages.get(i));
        }

        log.debug("加载历史轮次: conversationId={}, maxRounds={}, 实际消息数={}",
                conversationId, rounds, ordered.size());
        return ordered;
    }

    @Override
    public void updateTitle(Long conversationId, String firstMessage) {
        String title = extractTitle(firstMessage);
        Conversation conv = new Conversation();
        conv.setId(conversationId);
        conv.setTitle(title);
        conversationMapper.updateById(conv);
        log.info("更新会话标题: conversationId={}, title={}", conversationId, title);
    }

    @Override
    @Transactional
    public int cleanExpiredConversations(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        // 1. 查出过期会话 ID
        List<Conversation> expired = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .le(Conversation::getCreateTime, cutoff)
        );

        if (expired.isEmpty()) {
            log.info("无过期会话需要清理, 保留天数={}", retentionDays);
            return 0;
        }

        int count = 0;
        for (Conversation conv : expired) {
            // 删除关联消息
            messageMapper.delete(new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conv.getId()));
            // 删除会话
            conversationMapper.deleteById(conv.getId());
            count++;
        }

        log.info("过期会话清理完成: 清理{}个会话, 保留天数={}", count, retentionDays);
        return count;
    }

    // ==================== 私有方法 ====================

    /**
     * 从首条消息提取标题：去除空白，截取前 20 字，追加 "..."
     */
    private String extractTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) {
            return "新对话";
        }
        String cleaned = firstMessage.trim().replaceAll("\\s+", " ");
        if (cleaned.length() <= TITLE_MAX_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, TITLE_MAX_LENGTH) + "...";
    }
}
