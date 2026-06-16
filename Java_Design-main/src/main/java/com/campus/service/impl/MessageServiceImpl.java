package com.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.entity.Message;
import com.campus.repository.MessageMapper;
import com.campus.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);

    private final MessageMapper messageMapper;

    public MessageServiceImpl(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public List<Message> listByConversation(Long conversationId, int page, int size) {
        int offset = (page - 1) * size;

        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime)
                        .last("LIMIT " + offset + "," + size)
        );

        log.debug("分页查询消息: conversationId={}, page={}, size={}, 结果数={}",
                conversationId, page, size, messages.size());
        return messages;
    }

    @Override
    public long countByConversation(Long conversationId) {
        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
        );
        return count != null ? count : 0;
    }

    @Override
    public int deleteByConversation(Long conversationId) {
        int rows = messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId));
        log.info("按会话删除消息: conversationId={}, 删除条数={}", conversationId, rows);
        return rows;
    }
}
